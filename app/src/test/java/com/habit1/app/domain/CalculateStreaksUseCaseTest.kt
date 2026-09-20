package com.habit1.app.domain

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalculateStreaksUseCaseTest {

    private val useCase = CalculateStreaksUseCase()
    private val utcZone = ZoneId.of("UTC")
    private val baseInstant = Instant.parse("2026-09-01T00:00:00Z")

    private fun createRecord(dateStr: String, isCompleted: Boolean): HabitRecord {
        return HabitRecord(
            id = "rec_$dateStr",
            habitId = "test_h",
            date = LocalDate.parse(dateStr),
            actualValue = if (isCompleted) 1.0 else 0.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = isCompleted,
            recordedAt = Instant.parse("${dateStr}T12:00:00Z")
        )
    }

    @Test
    fun testAllConsecutiveDaysCompleted() {
        val habit = Habit(
            id = "test_h",
            name = "Study",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // 5 consecutive days completed (Sep 1 to Sep 5)
        val records = (1..5).map { createRecord("2026-09-0$it", true) }
        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)

        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak)
        assertEquals(5, result.totalCompletions)
        assertEquals(5, result.totalScheduledDays)
        assertEquals(100.0f, result.completionRate, 0.01f)
    }

    @Test
    fun testMissedDayResetsCurrentStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Study",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1: Done, Sep 2: Missed (no record), Sep 3: Done, Sep 4: Done, Sep 5: Done
        val records = listOf(
            createRecord("2026-09-01", true),
            // Sep 2 missing
            createRecord("2026-09-03", true),
            createRecord("2026-09-04", true),
            createRecord("2026-09-05", true)
        )

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)

        assertEquals(3, result.currentStreak) // Sep 3, 4, 5
        assertEquals(3, result.longestStreak)
        assertEquals(4, result.totalCompletions)
        assertEquals(5, result.totalScheduledDays)
        assertEquals(80.0f, result.completionRate, 0.01f) // 4 / 5 = 80%
    }

    @Test
    fun testNonScheduledDaysDoNotBreakStreak() {
        // Scheduled only Monday (2026-09-14), Wednesday (2026-09-16), Friday (2026-09-18)
        val habit = Habit(
            id = "test_h",
            name = "Gym",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            createdAt = Instant.parse("2026-09-14T00:00:00Z"),
            updatedAt = Instant.parse("2026-09-14T00:00:00Z")
        )

        val records = listOf(
            createRecord("2026-09-14", true), // Monday
            createRecord("2026-09-16", true), // Wednesday
            createRecord("2026-09-18", true)  // Friday
        )

        // On Saturday Sep 19 (non-scheduled day), the streak should still be 3!
        val resultSaturday = useCase.execute(habit, records, LocalDate.of(2026, 9, 19), utcZone)
        assertEquals(3, resultSaturday.currentStreak)
        assertEquals(3, resultSaturday.longestStreak)
        assertEquals(3, resultSaturday.totalCompletions)
        assertEquals(3, resultSaturday.totalScheduledDays)

        // On Sunday Sep 20 (non-scheduled day), the streak should still be 3!
        val resultSunday = useCase.execute(habit, records, LocalDate.of(2026, 9, 20), utcZone)
        assertEquals(3, resultSunday.currentStreak)
    }

    @Test
    fun testTodayNotCompletedYetPreservesOngoingStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Read",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Completed Sep 1 to Sep 4.
        // Today is Sep 5, not completed yet.
        val records = (1..4).map { createRecord("2026-09-0$it", true) }
        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)

        // Current streak should preserve the 4-day streak from yesterday without breaking it
        assertEquals(4, result.currentStreak)
        assertEquals(4, result.longestStreak)
        // Rate evaluated against past 4 days -> 4/4 = 100%
        assertEquals(100.0f, result.completionRate, 0.01f)
    }

    @Test
    fun testTodayCompletedIncrementsStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Read",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Completed Sep 1 to Sep 5 (today included)
        val records = (1..5).map { createRecord("2026-09-0$it", true) }
        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)

        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun testYesterdayMissedAndTodayNotDoneResultsInZeroStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Meditate",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Completed Sep 1, 2, 3. Sep 4 missed. Today is Sep 5 (not done).
        val records = listOf(
            createRecord("2026-09-01", true),
            createRecord("2026-09-02", true),
            createRecord("2026-09-03", true)
        )

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)
        assertEquals(0, result.currentStreak)
        assertEquals(3, result.longestStreak)
        assertEquals(3, result.totalCompletions)
    }

    @Test
    fun testEmptyHistoryReturnsZero() {
        val habit = Habit(
            id = "test_empty",
            name = "New Habit",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val result = useCase.execute(habit, emptyList(), LocalDate.of(2026, 9, 5), utcZone)
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.longestStreak)
        assertEquals(0, result.totalCompletions)
        assertEquals(0.0f, result.completionRate, 0.01f)
    }

    @Test
    fun testLongestStreakAcrossMultipleStreaks() {
        val habit = Habit(
            id = "test_h",
            name = "Coding",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Streak 1: 5 days (Sep 1 to Sep 5)
        // Miss: Sep 6
        // Streak 2: 2 days (Sep 7 to Sep 8)
        val records = mutableListOf<HabitRecord>()
        (1..5).forEach { records.add(createRecord("2026-09-0$it", true)) }
        records.add(createRecord("2026-09-07", true))
        records.add(createRecord("2026-09-08", true))

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 8), utcZone)
        assertEquals(2, result.currentStreak)
        assertEquals(5, result.longestStreak) // Correctly remembers the 5-day best
        assertEquals(7, result.totalCompletions)
    }
}
