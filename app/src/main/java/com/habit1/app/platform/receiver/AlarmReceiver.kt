package com.habit1.app.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habit1.app.HabitApplication
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.reminder.EveningDigestCalculator
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

/**
 * BroadcastReceiver triggered by AlarmManager when an alarm fires.
 *
 * Core Principle:
 * "Notifications are reminders, not records."
 * Receiving, displaying, or dismissing this reminder NEVER modifies habit_records or streaks.
 *
 * Defense-in-Depth & Completion Suppression:
 * - If habit is already marked DONE today: SUPPRESS completely (no notification, sound, or alert).
 * - PARTIAL quantitative progress does not suppress reminders.
 * - Re-validates the habit against database, startDate pre-creation boundary, pause intervals,
 *   and today's schedule before showing notification.
 * - Handles optional Evening Digest for scheduled habits summary.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_HABIT_REMINDER = "com.habit1.app.ACTION_HABIT_REMINDER"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"

        const val ACTION_EVENING_DIGEST = "com.habit1.app.ACTION_EVENING_DIGEST"

        const val ACTION_GOAL_REMINDER = "com.habit1.app.ACTION_GOAL_REMINDER"
        const val EXTRA_GOAL_ID = "extra_goal_id"
        const val EXTRA_GOAL_TITLE = "extra_goal_title"
        const val EXTRA_GOAL_NOTES = "extra_goal_notes"
    }

    private val evaluateSchedule = EvaluateScheduleUseCase()
    private val eveningDigestCalculator = EveningDigestCalculator(evaluateSchedule)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ACTION_GOAL_REMINDER -> handleGoalReminder(context, intent)
            ACTION_HABIT_REMINDER -> handleHabitReminder(context, intent)
            ACTION_EVENING_DIGEST -> handleEveningDigest(context)
        }
    }

    private fun handleGoalReminder(context: Context, intent: Intent) {
        val goalId = intent.getStringExtra(EXTRA_GOAL_ID) ?: return
        val goalTitle = intent.getStringExtra(EXTRA_GOAL_TITLE) ?: "Daily Goal"
        val goalNotes = intent.getStringExtra(EXTRA_GOAL_NOTES)
        val pendingResult = goAsync()
        val app = context.applicationContext as? HabitApplication
        if (app == null) {
            pendingResult?.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val goal = app.container.database.dailyGoalDao().getGoalById(goalId)
                if (goal != null && !goal.isCompleted) {
                    app.container.notificationHelper.showGoalReminderNotification(goalTitle, goalId, goalNotes)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun handleHabitReminder(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
            ?: intent.data?.lastPathSegment
            ?: return

        val pendingResult = goAsync()
        val app = context.applicationContext as? HabitApplication

        if (app == null) {
            pendingResult?.finish()
            return
        }

        val container = app.container
        val habitRepository = container.habitRepository
        val habitRecordRepository = container.habitRecordRepository
        val notificationHelper = container.notificationHelper
        val scheduler = container.reminderScheduler
        val database = container.database
        val zoneId = ZoneId.systemDefault()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entity = habitRepository.getHabitById(habitId)
                if (entity == null) {
                    scheduler.cancelReminder(habitId)
                    return@launch
                }

                val habit = entity.toDomain(zoneId)
                val today = DateTimeUtils.today(zoneId)
                val todayStr = DateTimeUtils.formatDate(today)

                // 1. Check pre-creation start date
                if (entity.startDate.isNotBlank()) {
                    val startLocalDate = try { DateTimeUtils.parseDate(entity.startDate) } catch (_: Exception) { null }
                    if (startLocalDate != null && today.isBefore(startLocalDate)) {
                        scheduleTomorrow(habit, today, scheduler, zoneId)
                        return@launch
                    }
                }

                // 2. Check temporal pauses
                val pauses = database.habitPauseDao().getPausesForHabit(habitId)
                val isPausedToday = habit.isPaused || pauses.any { pause ->
                    val pStart = try { DateTimeUtils.parseDate(pause.startDate) } catch (_: Exception) { null }
                    val pEnd = pause.endDate?.let { try { DateTimeUtils.parseDate(it) } catch (_: Exception) { null } }
                    if (pStart != null) {
                        if (pEnd != null) {
                            !today.isBefore(pStart) && !today.isAfter(pEnd)
                        } else {
                            !today.isBefore(pStart)
                        }
                    } else false
                }

                if (isPausedToday || habit.isArchived || habit.reminderTime == null) {
                    scheduler.cancelReminder(habitId)
                    notificationHelper.cancelNotification(habitId)
                    return@launch
                }

                // 3. Check schedule for today
                val isScheduledToday = evaluateSchedule.isScheduledOn(habit, today, zoneId)

                // 4. Re-check current database state for today's record
                val todayRecord = habitRecordRepository.getRecord(habitId, todayStr)
                val isDone = todayRecord != null && (todayRecord.isCompleted || todayRecord.status == HabitRecordStatus.DONE.name)
                val isSkipped = todayRecord != null && todayRecord.status == HabitRecordStatus.SKIPPED.name

                if (isDone || isSkipped) {
                    // SUPPRESS COMPLETELY: Eagerly dismiss notification, produce no sound/alert
                    notificationHelper.cancelNotification(habitId)
                } else if (isScheduledToday) {
                    // PARTIAL quantitative progress or UNRECORDED: Post reminder notification
                    notificationHelper.showReminderNotification(habit, todayStr)
                }

                // Schedule next upcoming reminder starting from tomorrow
                scheduleTomorrow(habit, today, scheduler, zoneId)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun handleEveningDigest(context: Context) {
        val pendingResult = goAsync()
        val app = context.applicationContext as? HabitApplication
        if (app == null) {
            pendingResult?.finish()
            return
        }

        val container = app.container
        val userPreferences = container.userPreferences
        val habitRepository = container.habitRepository
        val habitRecordRepository = container.habitRecordRepository
        val database = container.database
        val notificationHelper = container.notificationHelper
        val zoneId = ZoneId.systemDefault()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = userPreferences.userPreferencesFlow.first()
                if (!prefs.dailyReviewEnabled) {
                    return@launch
                }

                val today = DateTimeUtils.today(zoneId)
                val todayStr = DateTimeUtils.formatDate(today)

                val allHabits = habitRepository.observeAllHabits().first()
                val todayRecords = habitRecordRepository.getRecordsForDateRange(todayStr, todayStr)
                val pausesByHabit = mutableMapOf<String, List<com.habit1.app.data.local.db.entity.HabitPauseEntity>>()
                for (habit in allHabits) {
                    pausesByHabit[habit.id] = database.habitPauseDao().getPausesForHabit(habit.id)
                }

                val summary = eveningDigestCalculator.calculateDigest(
                    habits = allHabits,
                    records = todayRecords,
                    pausesByHabit = pausesByHabit,
                    date = today,
                    zoneId = zoneId
                )

                if (summary != null && summary.totalScheduledCount > 0) {
                    notificationHelper.showEveningDigestNotification(summary)
                }

                // Reschedule for next day's digest
                val digestTime = try {
                    LocalTime.parse(prefs.dailyReviewTime)
                } catch (_: Exception) {
                    LocalTime.of(21, 0)
                }
                container.eveningDigestScheduler.scheduleDigest(digestTime, zoneId)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun scheduleTomorrow(
        habit: com.habit1.app.domain.model.Habit,
        today: java.time.LocalDate,
        scheduler: com.habit1.app.platform.reminder.HabitReminderScheduler,
        zoneId: ZoneId
    ) {
        val tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant()
        scheduler.scheduleNextReminder(habit, fromInstant = tomorrowStart, zoneId = zoneId)
    }
}
