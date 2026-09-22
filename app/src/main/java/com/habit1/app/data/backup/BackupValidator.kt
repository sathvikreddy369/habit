package com.habit1.app.data.backup

import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.backup.model.HabitRecordBackupDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Preview summary information generated after successful validation of a backup file.
 */
data class BackupPreviewInfo(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val habitsCount: Int,
    val reminderHabitsCount: Int,
    val recordsCount: Int,
    val goalsCount: Int,
    val subtasksCount: Int,
    val reviewsCount: Int,
    val conflictingRecordsCount: Int = 0,
    val conflictingHabitsCount: Int = 0,
    val aggregatesCount: Int = 0
)

/**
 * Sealed class hierarchy for typed validation failures.
 */
sealed class BackupValidationError(val message: String) {
    class FileTooLarge(bytes: Long, maxBytes: Long) :
        BackupValidationError("Backup file size ($bytes bytes) exceeds maximum permitted ($maxBytes bytes).")
    class InvalidJson(details: String) :
        BackupValidationError("Malformed JSON: $details")
    class UnsupportedFormatVersion(val version: Int) :
        BackupValidationError("Unsupported format version $version. Current supported version is ${BackupEnvelopeDto.CURRENT_FORMAT_VERSION}.")
    object ChecksumMismatch :
        BackupValidationError("Integrity check failed: SHA-256 checksum does not match payload content.")
    class ResourceLimitExceeded(limitName: String, actual: Int, max: Int) :
        BackupValidationError("Resource limit exceeded for $limitName: $actual exceeds maximum of $max.")
    class StringLengthExceeded(field: String, actual: Int, max: Int) :
        BackupValidationError("String length for $field ($actual chars) exceeds maximum of $max chars.")
    class DuplicateId(collection: String, id: String) :
        BackupValidationError("Duplicate ID detected in $collection: '$id'.")
    class DuplicateKey(collection: String, key: String) :
        BackupValidationError("Duplicate unique constraint in $collection: '$key'.")
    class OrphanRelationship(childType: String, childId: String, parentType: String, parentId: String) :
        BackupValidationError("$childType '$childId' references non-existent $parentType '$parentId'.")
    class InvalidDomainInvariant(entityType: String, id: String, reason: String) :
        BackupValidationError("Invalid invariant for $entityType '$id': $reason")
}

sealed interface BackupValidationResult {
    data class Valid(val preview: BackupPreviewInfo) : BackupValidationResult
    data class Invalid(val error: BackupValidationError) : BackupValidationResult
}

/**
 * Pure domain validator for backup envelopes and payloads.
 * Validates format, limits, checksum, integrity, and domain constraints prior to any database mutation.
 */
object BackupValidator {

    const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024L // 20 MB
    const val MAX_HABITS = 500
    const val MAX_RECORDS = 50_000
    const val MAX_GOALS = 5_000
    const val MAX_SUBTASKS = 20_000
    const val MAX_REVIEWS = 5_000
    const val MAX_AGGREGATES = 10_000

    const val MAX_ID_LENGTH = 64
    const val MAX_NAME_LENGTH = 100
    const val MAX_NOTES_LENGTH = 5_000

    val VALID_MEASUREMENT_TYPES = setOf(
        com.habit1.app.domain.model.MeasurementType.BooleanChoice.TYPE_NAME, // "BOOLEAN"
        com.habit1.app.domain.model.MeasurementType.Count.TYPE_NAME,         // "COUNT"
        com.habit1.app.domain.model.MeasurementType.Duration.TYPE_NAME,      // "DURATION"
        com.habit1.app.domain.model.MeasurementType.Quantity.TYPE_NAME       // "QUANTITY"
    )
    val VALID_SCHEDULE_TYPES = setOf("DAILY", "SPECIFIC_DAYS", "INTERVAL")

    /**
     * Comprehensive validation of envelope, checksum, constraints, and relationships.
     *
     * @param envelope The parsed backup envelope.
     * @param existingHabitIds Optional set of existing habit IDs in local database (for Merge validation).
     * @param existingGoalIds Optional set of existing goal IDs in local database (for Merge validation).
     * @param existingRecordKeys Optional map of existing `(habitId, date) -> HabitRecordEntity` (to detect merge conflicts).
     */
    fun validate(
        envelope: BackupEnvelopeDto,
        existingHabitIds: Set<String> = emptySet(),
        existingGoalIds: Set<String> = emptySet(),
        existingRecordKeys: Set<Pair<String, String>> = emptySet()
    ): BackupValidationResult {
        // 1. Envelope & Versioning
        if (envelope.formatVersion != BackupEnvelopeDto.CURRENT_FORMAT_VERSION) {
            return BackupValidationResult.Invalid(
                BackupValidationError.UnsupportedFormatVersion(envelope.formatVersion)
            )
        }

        if (envelope.appVersion.isBlank()) {
            return BackupValidationResult.Invalid(
                BackupValidationError.InvalidDomainInvariant("Envelope", "appVersion", "App version cannot be blank.")
            )
        }

        if (envelope.exportedAt.isBlank()) {
            return BackupValidationResult.Invalid(
                BackupValidationError.InvalidDomainInvariant("Envelope", "exportedAt", "ExportedAt timestamp cannot be blank.")
            )
        }

        val payload = envelope.payload

        // 2. Resource Limits
        if (payload.habits.size > MAX_HABITS) {
            return BackupValidationResult.Invalid(
                BackupValidationError.ResourceLimitExceeded("Habits", payload.habits.size, MAX_HABITS)
            )
        }
        if (payload.records.size > MAX_RECORDS) {
            return BackupValidationResult.Invalid(
                BackupValidationError.ResourceLimitExceeded("Records", payload.records.size, MAX_RECORDS)
            )
        }
        if (payload.goals.size > MAX_GOALS) {
            return BackupValidationResult.Invalid(
                BackupValidationError.ResourceLimitExceeded("Goals", payload.goals.size, MAX_GOALS)
            )
        }
        if (payload.subtasks.size > MAX_SUBTASKS) {
            return BackupValidationResult.Invalid(
                BackupValidationError.ResourceLimitExceeded("Subtasks", payload.subtasks.size, MAX_SUBTASKS)
            )
        }
        if (payload.reviews.size > MAX_REVIEWS) {
            return BackupValidationResult.Invalid(
                BackupValidationError.ResourceLimitExceeded("Reviews", payload.reviews.size, MAX_REVIEWS)
            )
        }
        if (payload.aggregates.size > MAX_AGGREGATES) {
            return BackupValidationResult.Invalid(
                BackupValidationError.ResourceLimitExceeded("Aggregates", payload.aggregates.size, MAX_AGGREGATES)
            )
        }

        // 3. SHA-256 Checksum Verification
        if (!BackupChecksumCalculator.verifyChecksum(payload, envelope.checksum)) {
            return BackupValidationResult.Invalid(BackupValidationError.ChecksumMismatch)
        }

        // 4. Collection Uniqueness & Entity Invariants
        val habitIds = mutableSetOf<String>()
        var reminderHabitsCount = 0
        for (habit in payload.habits) {
            if (habit.id.length > MAX_ID_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("habit.id", habit.id.length, MAX_ID_LENGTH)
                )
            }
            if (habit.id.isBlank()) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "ID cannot be blank.")
                )
            }
            if (!habitIds.add(habit.id)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.DuplicateId("habits", habit.id)
                )
            }
            if (habit.name.isBlank()) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Name cannot be blank.")
                )
            }
            if (habit.name.length > MAX_NAME_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("habit.name", habit.name.length, MAX_NAME_LENGTH)
                )
            }
            when (habit.measurementType) {
                com.habit1.app.domain.model.MeasurementType.BooleanChoice.TYPE_NAME -> {
                    if (habit.targetValue != 1.0) {
                        return BackupValidationResult.Invalid(
                            BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Boolean habit targetValue must be 1.0, got ${habit.targetValue}.")
                        )
                    }
                }
                com.habit1.app.domain.model.MeasurementType.Count.TYPE_NAME -> {
                    if (habit.targetValue < 1.0 || habit.targetValue % 1.0 != 0.0) {
                        return BackupValidationResult.Invalid(
                            BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Count habit targetValue must be a positive integer, got ${habit.targetValue}.")
                        )
                    }
                }
                com.habit1.app.domain.model.MeasurementType.Duration.TYPE_NAME -> {
                    if (habit.targetValue < 1.0 || habit.targetValue % 1.0 != 0.0) {
                        return BackupValidationResult.Invalid(
                            BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Duration habit targetValue must be positive minutes, got ${habit.targetValue}.")
                        )
                    }
                }
                com.habit1.app.domain.model.MeasurementType.Quantity.TYPE_NAME -> {
                    if (habit.targetValue <= 0.0) {
                        return BackupValidationResult.Invalid(
                            BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Quantity habit targetValue must be positive, got ${habit.targetValue}.")
                        )
                    }
                    if (habit.unit.isNullOrBlank()) {
                        return BackupValidationResult.Invalid(
                            BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Quantity habit requires a non-blank unit.")
                        )
                    }
                }
                else -> {
                    return BackupValidationResult.Invalid(
                        BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Unknown measurementType '${habit.measurementType}'.")
                    )
                }
            }
            if (habit.scheduleType !in VALID_SCHEDULE_TYPES) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Unknown scheduleType '${habit.scheduleType}'.")
                )
            }
            if (habit.reminderTime != null) {
                if (!isValidLocalTime(habit.reminderTime)) {
                    return BackupValidationResult.Invalid(
                        BackupValidationError.InvalidDomainInvariant("Habit", habit.id, "Invalid reminder time '${habit.reminderTime}'.")
                    )
                }
                if (!habit.isPaused && !habit.isArchived) {
                    reminderHabitsCount++
                }
            }
        }

        // Validate Records
        val recordIds = mutableSetOf<String>()
        val recordHabitDatePairs = mutableSetOf<Pair<String, String>>()
        val allValidHabitIds = habitIds + existingHabitIds

        var conflictingRecordsCount = 0
        for (record in payload.records) {
            if (record.id.length > MAX_ID_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("record.id", record.id.length, MAX_ID_LENGTH)
                )
            }
            if (record.id.isBlank()) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Record", record.id, "ID cannot be blank.")
                )
            }
            if (!recordIds.add(record.id)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.DuplicateId("records", record.id)
                )
            }
            val habitDatePair = Pair(record.habitId, record.date)
            if (!recordHabitDatePairs.add(habitDatePair)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.DuplicateKey("records", "(${record.habitId}, ${record.date})")
                )
            }
            // Foreign Key Check
            if (!allValidHabitIds.contains(record.habitId)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.OrphanRelationship("HabitRecord", record.id, "Habit", record.habitId)
                )
            }
            if (!isValidLocalDate(record.date)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Record", record.id, "Invalid calendar date '${record.date}'.")
                )
            }
            if (record.actualValue < 0.0) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Record", record.id, "Actual value cannot be negative, got ${record.actualValue}.")
                )
            }
            if (record.targetValue <= 0.0) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Record", record.id, "Target value must be positive, got ${record.targetValue}.")
                )
            }
            if (record.measurementType !in VALID_MEASUREMENT_TYPES) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("Record", record.id, "Unknown measurementType '${record.measurementType}'.")
                )
            }
            if (record.notes != null && record.notes.length > MAX_NOTES_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("record.notes", record.notes.length, MAX_NOTES_LENGTH)
                )
            }

            if (existingRecordKeys.contains(habitDatePair)) {
                conflictingRecordsCount++
            }
        }

        // Validate Daily Goals
        val goalIds = mutableSetOf<String>()
        for (goal in payload.goals) {
            if (goal.id.length > MAX_ID_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("goal.id", goal.id.length, MAX_ID_LENGTH)
                )
            }
            if (goal.id.isBlank()) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyGoal", goal.id, "ID cannot be blank.")
                )
            }
            if (!goalIds.add(goal.id)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.DuplicateId("goals", goal.id)
                )
            }
            if (goal.title.isBlank()) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyGoal", goal.id, "Title cannot be blank.")
                )
            }
            if (goal.title.length > MAX_NAME_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("goal.title", goal.title.length, MAX_NAME_LENGTH)
                )
            }
            if (!isValidLocalDate(goal.targetDate)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyGoal", goal.id, "Invalid target date '${goal.targetDate}'.")
                )
            }
            if (goal.notes != null && goal.notes.length > MAX_NOTES_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("goal.notes", goal.notes.length, MAX_NOTES_LENGTH)
                )
            }
        }

        // Validate Goal Subtasks
        val subtaskIds = mutableSetOf<String>()
        val allValidGoalIds = goalIds + existingGoalIds
        for (subtask in payload.subtasks) {
            if (subtask.id.length > MAX_ID_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("subtask.id", subtask.id.length, MAX_ID_LENGTH)
                )
            }
            if (subtask.id.isBlank()) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("GoalSubtask", subtask.id, "ID cannot be blank.")
                )
            }
            if (!subtaskIds.add(subtask.id)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.DuplicateId("subtasks", subtask.id)
                )
            }
            // Foreign Key Check
            if (!allValidGoalIds.contains(subtask.goalId)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.OrphanRelationship("GoalSubtask", subtask.id, "DailyGoal", subtask.goalId)
                )
            }
            if (subtask.title.isBlank()) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("GoalSubtask", subtask.id, "Title cannot be blank.")
                )
            }
            if (subtask.title.length > MAX_NAME_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("subtask.title", subtask.title.length, MAX_NAME_LENGTH)
                )
            }
        }

        // Validate Daily Reviews
        val reviewDates = mutableSetOf<String>()
        for (review in payload.reviews) {
            if (!isValidLocalDate(review.date)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyReview", review.date, "Invalid review date '${review.date}'.")
                )
            }
            if (!reviewDates.add(review.date)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.DuplicateKey("reviews", review.date)
                )
            }
            if (review.notes != null && review.notes.length > MAX_NOTES_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("review.notes", review.notes.length, MAX_NOTES_LENGTH)
                )
            }
            if (review.mood != null && review.mood.length > MAX_NAME_LENGTH) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.StringLengthExceeded("review.mood", review.mood.length, MAX_NAME_LENGTH)
                )
            }
        }

        // 9. Aggregates Invariants & Constraints
        val aggregateDates = mutableSetOf<String>()
        for (aggregate in payload.aggregates) {
            if (!isValidLocalDate(aggregate.date)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyGoalHistoryAggregate", aggregate.date, "Invalid aggregate date '${aggregate.date}'.")
                )
            }
            if (!aggregateDates.add(aggregate.date)) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.DuplicateKey("aggregates", aggregate.date)
                )
            }
            if (aggregate.completedCount < 0) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyGoalHistoryAggregate", aggregate.date, "completedCount cannot be negative: ${aggregate.completedCount}")
                )
            }
            if (aggregate.totalCount < 0) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyGoalHistoryAggregate", aggregate.date, "totalCount cannot be negative: ${aggregate.totalCount}")
                )
            }
            if (aggregate.completedCount > aggregate.totalCount) {
                return BackupValidationResult.Invalid(
                    BackupValidationError.InvalidDomainInvariant("DailyGoalHistoryAggregate", aggregate.date, "completedCount (${aggregate.completedCount}) cannot exceed totalCount (${aggregate.totalCount})")
                )
            }
        }

        val conflictingHabitsCount = habitIds.intersect(existingHabitIds).size

        val preview = BackupPreviewInfo(
            formatVersion = envelope.formatVersion,
            appVersion = envelope.appVersion,
            exportedAt = envelope.exportedAt,
            habitsCount = payload.habits.size,
            reminderHabitsCount = reminderHabitsCount,
            recordsCount = payload.records.size,
            goalsCount = payload.goals.size,
            subtasksCount = payload.subtasks.size,
            reviewsCount = payload.reviews.size,
            conflictingRecordsCount = conflictingRecordsCount,
            conflictingHabitsCount = conflictingHabitsCount,
            aggregatesCount = payload.aggregates.size
        )

        return BackupValidationResult.Valid(preview)
    }

    private fun isValidLocalDate(dateStr: String): Boolean {
        return try {
            LocalDate.parse(dateStr)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }

    private fun isValidLocalTime(timeStr: String): Boolean {
        return try {
            LocalTime.parse(timeStr)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}
