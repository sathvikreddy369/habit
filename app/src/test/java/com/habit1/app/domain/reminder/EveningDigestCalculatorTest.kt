package com.habit1.app.domain.reminder

import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitPauseEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.model.HabitRecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EveningDigestCalculatorTest {

    private val calculator = EveningDigestCalculator()
    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 25)
    private val todayStr = "2026-11-25"
    private val pastMillis = 1788220800000L

    private fun createHabit(
        id: String,
        name: String,
        startDate: String = "2026-01-01",
        isPaused: Boolean = false,
        isArchived: Boolean = false
    ): HabitEntity {
        return HabitEntity(
            id = id,
            name = name,
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = isPaused,
            isArchived = isArchived,
            startDate = startDate,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
    }

    @Test
    fun testDigestCalculation_completedAndPending() {
        val h1 = createHabit("h1", "Exercise")
        val h2 = createHabit("h2", "Read")
        val h3 = createHabit("h3", "Meditate")

        val r1 = HabitRecordEntity(
            id = "r1",
            habitId = "h1",
            date = todayStr,
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = true,
            status = HabitRecordStatus.DONE.name,
            recordedAt = pastMillis
        )
        // h2 has partial progress
        val r2 = HabitRecordEntity(
            id = "r2",
            habitId = "h2",
            date = todayStr,
            actualValue = 0.5,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = false,
            status = HabitRecordStatus.PARTIAL.name,
            recordedAt = pastMillis
        )
        // h3 unrecorded

        val summary = calculator.calculateDigest(
            habits = listOf(h1, h2, h3),
            records = listOf(r1, r2),
            pausesByHabit = emptyMap(),
            date = today,
            zoneId = zoneId
        )

        assertNotNull(summary)
        assertEquals(1, summary!!.completedCount) // h1
        assertEquals(2, summary.pendingCount)    // h2 (partial) + h3 (unrecorded)
        assertEquals(3, summary.totalScheduledCount)
    }

    @Test
    fun testDigestCalculation_skipsAndExclusions() {
        val hActive = createHabit("h_active", "Hydrate")
        val hSkipped = createHabit("h_skipped", "Run")
        val hPaused = createHabit("h_paused", "Swim", isPaused = true)
        val hArchived = createHabit("h_archived", "Old Habit", isArchived = true)
        val hFuture = createHabit("h_future", "Future Habit", startDate = "2026-12-01")

        // Record for hSkipped
        val rSkipped = HabitRecordEntity(
            id = "r_skip",
            habitId = "h_skipped",
            date = todayStr,
            actualValue = 0.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = false,
            status = HabitRecordStatus.SKIPPED.name,
            recordedAt = pastMillis
        )

        val summary = calculator.calculateDigest(
            habits = listOf(hActive, hSkipped, hPaused, hArchived, hFuture),
            records = listOf(rSkipped),
            pausesByHabit = emptyMap(),
            date = today,
            zoneId = zoneId
        )

        assertNotNull(summary)
        // Only hActive is eligible scheduled. hSkipped is excused, paused/archived/future excluded
        assertEquals(0, summary!!.completedCount)
        assertEquals(1, summary.pendingCount)
        assertEquals(1, summary.totalScheduledCount)
    }

    @Test
    fun testDigestCalculation_noScheduledHabits_returnsNull() {
        val hPaused = createHabit("h_paused", "Swim", isPaused = true)

        val summary = calculator.calculateDigest(
            habits = listOf(hPaused),
            records = emptyList(),
            pausesByHabit = emptyMap(),
            date = today,
            zoneId = zoneId
        )

        assertNull(summary)
    }
}
