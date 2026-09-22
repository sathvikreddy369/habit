package com.habit1.app.data.backup

import androidx.room.withTransaction
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.toEntity
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler

/**
 * Mode of restore operation selected by the user.
 */
enum class RestoreMode {
    /**
     * Explicitly destructive: Wipes existing database and replaces all data with backup.
     */
    ReplaceAll,

    /**
     * Non-destructive: Merges backup data into current database according to defined conflict rules.
     */
    Merge
}

/**
 * Summary of a restore operation.
 */
data class BackupRestoreSummary(
    val mode: RestoreMode,
    val habitsRestored: Int,
    val recordsRestored: Int,
    val goalsRestored: Int,
    val subtasksRestored: Int,
    val reviewsRestored: Int,
    val conflictingRecordsPreserved: Int = 0,
    val aggregatesRestored: Int = 0
)

/**
 * Executes atomic, transactional restoration of validated backup envelopes.
 */
class BackupImporter(
    private val database: AppDatabase,
    private val reminderCoordinator: HabitReminderCoordinator,
    private val reminderScheduler: HabitReminderScheduler,
    private val notificationHelper: NotificationHelper
) {

    /**
     * Restores a pre-validated backup envelope into the database using the specified mode.
     */
    suspend fun restore(envelope: BackupEnvelopeDto, mode: RestoreMode): BackupRestoreSummary {
        return when (mode) {
            RestoreMode.ReplaceAll -> executeReplaceAll(envelope)
            RestoreMode.Merge -> executeMerge(envelope)
        }
    }

    private suspend fun executeReplaceAll(envelope: BackupEnvelopeDto): BackupRestoreSummary {
        val payload = envelope.payload

        // 1. Collect all current habits with active reminders and cancel alarms BEFORE database wipe.
        val currentHabits = database.habitDao().getAllHabitsList()
        val habitsWithReminders = currentHabits.filter {
            it.reminderTime != null && !it.isPaused && !it.isArchived
        }

        for (habit in habitsWithReminders) {
            reminderScheduler.cancelReminder(habit.id)
            notificationHelper.cancelNotification(habit.id)
        }

        try {
            // 2. Execute database wipe and insertion inside a single Room transaction
            // Foreign-key enforcement remains active; operations must occur in strict child-to-parent delete
            // and parent-to-child insert order.
            database.withTransaction {
                // Child-to-parent deletion
                database.dailyGoalDao().deleteAllAggregates()
                database.dailyGoalDao().deleteAllSubtasks()
                database.dailyGoalDao().deleteAllGoals()
                database.habitRecordDao().deleteAllRecords()
                database.habitDao().deleteAllHabits()
                database.dailyReviewDao().deleteAllReviews()

                // Parent-to-child insertion
                if (payload.habits.isNotEmpty()) {
                    database.habitDao().insertAll(payload.habits.map { it.toEntity() })
                }
                if (payload.records.isNotEmpty()) {
                    database.habitRecordDao().insertAll(payload.records.map { it.toEntity() })
                }
                if (payload.goals.isNotEmpty()) {
                    database.dailyGoalDao().insertAllGoals(payload.goals.map { it.toEntity() })
                }
                if (payload.subtasks.isNotEmpty()) {
                    database.dailyGoalDao().insertAllSubtasks(payload.subtasks.map { it.toEntity() })
                }
                if (payload.reviews.isNotEmpty()) {
                    database.dailyReviewDao().upsertAll(payload.reviews.map { it.toEntity() })
                }
                if (payload.aggregates.isNotEmpty()) {
                    database.dailyGoalDao().upsertAllAggregates(payload.aggregates.map { it.toEntity() })
                }
            }
        } catch (t: Throwable) {
            // Transaction Failure Recovery: Room rolled back table changes.
            // Reconcile reminders against the restored original database to recover cancelled alarms.
            try {
                reminderCoordinator.reconcileAllReminders()
            } catch (reconcileError: Throwable) {
                reconcileError.printStackTrace()
            }
            throw t
        }

        // 3. Post-Commit Alarm Synchronization
        reminderCoordinator.reconcileAllReminders()

        return BackupRestoreSummary(
            mode = RestoreMode.ReplaceAll,
            habitsRestored = payload.habits.size,
            recordsRestored = payload.records.size,
            goalsRestored = payload.goals.size,
            subtasksRestored = payload.subtasks.size,
            reviewsRestored = payload.reviews.size,
            aggregatesRestored = payload.aggregates.size
        )
    }

    private suspend fun executeMerge(envelope: BackupEnvelopeDto): BackupRestoreSummary {
        val payload = envelope.payload
        var conflictingRecordsPreserved = 0

        database.withTransaction {
            val existingHabits = database.habitDao().getAllHabitsList().associateBy { it.id }
            val existingGoals = database.dailyGoalDao().getAllGoalsList().associateBy { it.id }
            val existingSubtasks = database.dailyGoalDao().getAllSubtasksList().associateBy { it.id }
            val existingReviews = database.dailyReviewDao().getAllReviews().associateBy { it.date }
            val existingRecords = database.habitRecordDao().getAllRecordsList().associateBy {
                Pair(it.habitId, it.date)
            }
            val existingAggregates = database.dailyGoalDao().getAllAggregates().associateBy { it.date }

            // 1. Merge Habits
            for (backupHabit in payload.habits) {
                val current = existingHabits[backupHabit.id]
                if (current == null) {
                    database.habitDao().insert(backupHabit.toEntity())
                } else if (backupHabit.updatedAt > current.updatedAt) {
                    database.habitDao().update(backupHabit.toEntity())
                }
                // If backup.updatedAt <= current.updatedAt, keep current
            }

            // 2. Merge Daily Goals
            for (backupGoal in payload.goals) {
                val current = existingGoals[backupGoal.id]
                if (current == null) {
                    database.dailyGoalDao().insertGoal(backupGoal.toEntity())
                } else if (backupGoal.updatedAt > current.updatedAt) {
                    database.dailyGoalDao().updateGoal(backupGoal.toEntity())
                }
            }

            // 3. Merge Goal Subtasks
            for (backupSubtask in payload.subtasks) {
                val current = existingSubtasks[backupSubtask.id]
                if (current == null) {
                    database.dailyGoalDao().insertSubtask(backupSubtask.toEntity())
                }
                // If subtask exists, preserve current DB state (current wins)
            }

            // 4. Merge Daily Reviews
            for (backupReview in payload.reviews) {
                val current = existingReviews[backupReview.date]
                if (current == null) {
                    database.dailyReviewDao().upsert(backupReview.toEntity())
                } else if (backupReview.updatedAt > current.updatedAt) {
                    database.dailyReviewDao().upsert(backupReview.toEntity())
                }
            }

            // 5. Merge Habit Records (Historical Facts Rule)
            for (backupRecord in payload.records) {
                val key = Pair(backupRecord.habitId, backupRecord.date)
                val current = existingRecords[key]
                if (current == null) {
                    database.habitRecordDao().upsert(backupRecord.toEntity())
                } else {
                    // Check if content matches
                    val isIdentical = current.actualValue == backupRecord.actualValue &&
                            current.targetValue == backupRecord.targetValue &&
                            current.isCompleted == backupRecord.isCompleted &&
                            current.unit == backupRecord.unit &&
                            current.notes == backupRecord.notes

                    if (!isIdentical) {
                        // Preserves existing local history; does not silently overwrite
                        conflictingRecordsPreserved++
                    }
                }
            }

            // 6. Merge Historical Daily Goal Aggregates
            for (backupAggregate in payload.aggregates) {
                val current = existingAggregates[backupAggregate.date]
                if (current == null) {
                    database.dailyGoalDao().upsertAggregate(backupAggregate.toEntity())
                } else if (backupAggregate.totalCount > current.totalCount ||
                    (backupAggregate.totalCount == current.totalCount && backupAggregate.completedCount > current.completedCount)) {
                    database.dailyGoalDao().upsertAggregate(backupAggregate.toEntity())
                }
            }
        }

        // Post-Merge Alarm Synchronization
        reminderCoordinator.reconcileAllReminders()

        return BackupRestoreSummary(
            mode = RestoreMode.Merge,
            habitsRestored = payload.habits.size,
            recordsRestored = payload.records.size,
            goalsRestored = payload.goals.size,
            subtasksRestored = payload.subtasks.size,
            reviewsRestored = payload.reviews.size,
            conflictingRecordsPreserved = conflictingRecordsPreserved,
            aggregatesRestored = payload.aggregates.size
        )
    }
}
