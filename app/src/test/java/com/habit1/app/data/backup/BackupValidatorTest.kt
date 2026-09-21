package com.habit1.app.data.backup

import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.backup.model.DailyGoalBackupDto
import com.habit1.app.data.backup.model.DailyReviewBackupDto
import com.habit1.app.data.backup.model.GoalSubtaskBackupDto
import com.habit1.app.data.backup.model.HabitBackupDto
import com.habit1.app.data.backup.model.HabitRecordBackupDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {

    private fun createValidEnvelope(): BackupEnvelopeDto {
        val payload = BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "h1",
                    name = "Morning Meditation",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "07:00",
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            ),
            records = listOf(
                HabitRecordBackupDto(
                    id = "r1",
                    habitId = "h1",
                    date = "2026-09-21",
                    isCompleted = true,
                    actualValue = 1.0,
                    targetValue = 1.0,
                    unit = null,
                    measurementType = "BOOLEAN",
                    notes = null,
                    recordedAt = 1050L
                )
            ),
            goals = listOf(
                DailyGoalBackupDto(
                    id = "g1",
                    targetDate = "2026-09-21",
                    title = "Workout",
                    notes = null,
                    isCompleted = false,
                    displayOrder = 0,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            ),
            subtasks = listOf(
                GoalSubtaskBackupDto(
                    id = "s1",
                    goalId = "g1",
                    title = "Warmup",
                    isCompleted = true,
                    displayOrder = 0,
                    createdAt = 1000L
                )
            ),
            reviews = listOf(
                DailyReviewBackupDto(
                    date = "2026-09-21",
                    notes = "Good morning",
                    mood = "CALM",
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        )

        return BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-21T12:00:00Z",
            checksum = BackupChecksumCalculator.computeChecksum(payload),
            payload = payload
        )
    }

    @Test
    fun testValidEnvelope_passesValidation() {
        val envelope = createValidEnvelope()
        val result = BackupValidator.validate(envelope)

        assertTrue(result is BackupValidationResult.Valid)
        val preview = (result as BackupValidationResult.Valid).preview
        assertEquals(1, preview.habitsCount)
        assertEquals(1, preview.reminderHabitsCount)
        assertEquals(1, preview.recordsCount)
        assertEquals(1, preview.goalsCount)
        assertEquals(1, preview.subtasksCount)
        assertEquals(1, preview.reviewsCount)
    }

    @Test
    fun testUnsupportedFormatVersion_rejected() {
        val valid = createValidEnvelope()
        val futureEnvelope = valid.copy(formatVersion = 2)
        val result = BackupValidator.validate(futureEnvelope)

        assertTrue(result is BackupValidationResult.Invalid)
        val error = (result as BackupValidationResult.Invalid).error
        assertTrue(error is BackupValidationError.UnsupportedFormatVersion)
        assertEquals(2, (error as BackupValidationError.UnsupportedFormatVersion).version)
    }

    @Test
    fun testChecksumMismatch_rejected() {
        val valid = createValidEnvelope()
        val tamperedEnvelope = valid.copy(checksum = "0000000000000000000000000000000000000000000000000000000000000000")
        val result = BackupValidator.validate(tamperedEnvelope)

        assertTrue(result is BackupValidationResult.Invalid)
        val error = (result as BackupValidationResult.Invalid).error
        assertTrue(error is BackupValidationError.ChecksumMismatch)
    }

    @Test
    fun testDuplicateHabitId_rejected() {
        val habit1 = createValidEnvelope().payload.habits[0]
        val habitDuplicate = habit1.copy(name = "Duplicate Name")
        val payload = createValidEnvelope().payload.copy(habits = listOf(habit1, habitDuplicate))
        val envelope = createValidEnvelope().copy(
            payload = payload,
            checksum = BackupChecksumCalculator.computeChecksum(payload)
        )

        val result = BackupValidator.validate(envelope)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).error is BackupValidationError.DuplicateId)
    }

    @Test
    fun testDuplicateRecordDate_rejected() {
        val baseEnvelope = createValidEnvelope()
        val rec1 = baseEnvelope.payload.records[0]
        val rec2 = rec1.copy(id = "r2", isCompleted = false)
        val payload = baseEnvelope.payload.copy(records = listOf(rec1, rec2))
        val envelope = baseEnvelope.copy(
            payload = payload,
            checksum = BackupChecksumCalculator.computeChecksum(payload)
        )

        val result = BackupValidator.validate(envelope)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).error is BackupValidationError.DuplicateKey)
    }

    @Test
    fun testOrphanRecord_rejected() {
        val baseEnvelope = createValidEnvelope()
        val orphanRecord = baseEnvelope.payload.records[0].copy(habitId = "non_existent_habit")
        val payload = baseEnvelope.payload.copy(records = listOf(orphanRecord))
        val envelope = baseEnvelope.copy(
            payload = payload,
            checksum = BackupChecksumCalculator.computeChecksum(payload)
        )

        val result = BackupValidator.validate(envelope)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).error is BackupValidationError.OrphanRelationship)
    }

    @Test
    fun testOrphanSubtask_rejected() {
        val baseEnvelope = createValidEnvelope()
        val orphanSubtask = baseEnvelope.payload.subtasks[0].copy(goalId = "non_existent_goal")
        val payload = baseEnvelope.payload.copy(subtasks = listOf(orphanSubtask))
        val envelope = baseEnvelope.copy(
            payload = payload,
            checksum = BackupChecksumCalculator.computeChecksum(payload)
        )

        val result = BackupValidator.validate(envelope)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).error is BackupValidationError.OrphanRelationship)
    }

    @Test
    fun testInvalidDate_rejected() {
        val baseEnvelope = createValidEnvelope()
        val badDateRecord = baseEnvelope.payload.records[0].copy(date = "2026-02-31") // Invalid leap/Feb date
        val payload = baseEnvelope.payload.copy(records = listOf(badDateRecord))
        val envelope = baseEnvelope.copy(
            payload = payload,
            checksum = BackupChecksumCalculator.computeChecksum(payload)
        )

        val result = BackupValidator.validate(envelope)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).error is BackupValidationError.InvalidDomainInvariant)
    }

    @Test
    fun testInvalidReminderTime_rejected() {
        val baseEnvelope = createValidEnvelope()
        val badHabit = baseEnvelope.payload.habits[0].copy(reminderTime = "25:70")
        val payload = baseEnvelope.payload.copy(habits = listOf(badHabit))
        val envelope = baseEnvelope.copy(
            payload = payload,
            checksum = BackupChecksumCalculator.computeChecksum(payload)
        )

        val result = BackupValidator.validate(envelope)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).error is BackupValidationError.InvalidDomainInvariant)
    }

    @Test
    fun testNegativeActualValue_rejected() {
        val baseEnvelope = createValidEnvelope()
        val badRecord = baseEnvelope.payload.records[0].copy(actualValue = -5.0)
        val payload = baseEnvelope.payload.copy(records = listOf(badRecord))
        val envelope = baseEnvelope.copy(
            payload = payload,
            checksum = BackupChecksumCalculator.computeChecksum(payload)
        )

        val result = BackupValidator.validate(envelope)
        assertTrue(result is BackupValidationResult.Invalid)
        assertTrue((result as BackupValidationResult.Invalid).error is BackupValidationError.InvalidDomainInvariant)
    }
}
