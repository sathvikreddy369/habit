package com.habit1.app.data.backup

import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.backup.model.DailyGoalBackupDto
import com.habit1.app.data.backup.model.DailyReviewBackupDto
import com.habit1.app.data.backup.model.GoalSubtaskBackupDto
import com.habit1.app.data.backup.model.HabitBackupDto
import com.habit1.app.data.backup.model.HabitRecordBackupDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupChecksumCalculatorTest {

    private fun createSamplePayload(): BackupPayloadDto {
        return BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "h1",
                    name = "Read Books",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "20:00",
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 1000L,
                    updatedAt = 1000L
                ),
                HabitBackupDto(
                    id = "h2",
                    name = "Pushups",
                    measurementType = "COUNT",
                    targetValue = 50.0,
                    unit = "reps",
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = null,
                    displayOrder = 1,
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
                    notes = "Finished chapter 4",
                    recordedAt = 1050L
                )
            ),
            goals = listOf(
                DailyGoalBackupDto(
                    id = "g1",
                    targetDate = "2026-09-21",
                    title = "Prepare quarterly review",
                    notes = "Include marketing KPIs",
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
                    title = "Gather slides",
                    isCompleted = true,
                    displayOrder = 0,
                    createdAt = 1000L
                )
            ),
            reviews = listOf(
                DailyReviewBackupDto(
                    date = "2026-09-21",
                    notes = "Productive day",
                    mood = "FOCUSED",
                    createdAt = 1100L,
                    updatedAt = 1100L
                )
            )
        )
    }

    @Test
    fun testChecksumDeterministic() {
        val payload1 = createSamplePayload()
        val payload2 = createSamplePayload()

        val checksum1 = BackupChecksumCalculator.computeChecksum(payload1)
        val checksum2 = BackupChecksumCalculator.computeChecksum(payload2)

        assertEquals(checksum1, checksum2)
        assertEquals(64, checksum1.length) // 256 bits = 64 hex characters
        assertTrue(BackupChecksumCalculator.verifyChecksum(payload1, checksum1))
    }

    @Test
    fun testCollectionOrderIndependence() {
        val payloadAscending = createSamplePayload()
        // Reverse order of habits
        val payloadReversed = payloadAscending.copy(
            habits = payloadAscending.habits.reversed()
        )

        val checksumAsc = BackupChecksumCalculator.computeChecksum(payloadAscending)
        val checksumRev = BackupChecksumCalculator.computeChecksum(payloadReversed)

        // Sorting in canonicalizer must produce identical checksums
        assertEquals(checksumAsc, checksumRev)
    }

    @Test
    fun testTamperingDetection_singleFieldModified() {
        val payload = createSamplePayload()
        val validChecksum = BackupChecksumCalculator.computeChecksum(payload)

        // Modify a record's notes
        val tamperedPayload = payload.copy(
            records = listOf(
                payload.records[0].copy(notes = "Tampered notes")
            )
        )

        val tamperedChecksum = BackupChecksumCalculator.computeChecksum(tamperedPayload)
        assertNotEquals(validChecksum, tamperedChecksum)
        assertFalse(BackupChecksumCalculator.verifyChecksum(tamperedPayload, validChecksum))
    }

    @Test
    fun testTamperingDetection_targetValueModified() {
        val payload = createSamplePayload()
        val validChecksum = BackupChecksumCalculator.computeChecksum(payload)

        val tamperedPayload = payload.copy(
            habits = listOf(
                payload.habits[0].copy(targetValue = 999.0),
                payload.habits[1]
            )
        )

        assertFalse(BackupChecksumCalculator.verifyChecksum(tamperedPayload, validChecksum))
    }
}
