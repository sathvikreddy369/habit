package com.habit1.app.domain

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitPause
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalculateStreaksUseCaseTest {

    private val useCase = CalculateStreaksUseCase()
    private val utcZone = ZoneId.of("UTC")
    private val baseInstant = Instant.parse("2026-09-01T00:00:00Z")

    private fun createRecord(
        dateStr: String,
        isCompleted: Boolean,
        status: HabitRecordStatus = if (isCompleted) HabitRecordStatus.DONE else HabitRecordStatus.MISSED,
        actualValue: Double = if (isCompleted) 1.0 else 0.0
    ): HabitRecord {
        return HabitRecord(
            id = "rec_$dateStr",
            habitId = "test_h",
            date = LocalDate.parse(dateStr),
            actualValue = actualValue,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            status = status,
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

    @Test
    fun testSkippedActsAsNeutralBridgeAndPreservesStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Yoga",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1: DONE, Sep 2: DONE, Sep 3: SKIPPED, Sep 4: DONE, Sep 5: DONE
        val records = listOf(
            createRecord("2026-09-01", true, HabitRecordStatus.DONE),
            createRecord("2026-09-02", true, HabitRecordStatus.DONE),
            createRecord("2026-09-03", false, HabitRecordStatus.SKIPPED),
            createRecord("2026-09-04", true, HabitRecordStatus.DONE),
            createRecord("2026-09-05", true, HabitRecordStatus.DONE)
        )

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)
        // 4 completed occurrences bridged by 1 skip
        assertEquals(4, result.currentStreak)
        assertEquals(4, result.longestStreak)
        assertEquals(4, result.totalCompletions)
        assertEquals(1, result.totalSkippedDays)
        assertFalse(result.isFrozen)
        // Completion rate: 4 completed / (5 scheduled - 1 skipped) * 100 = 100.0%
        assertEquals(100.0f, result.completionRate, 0.01f)
    }

    @Test
    fun testTodaySkippedFreezesStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Yoga",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1 to Sep 4: DONE. Today (Sep 5): SKIPPED
        val records = listOf(
            createRecord("2026-09-01", true, HabitRecordStatus.DONE),
            createRecord("2026-09-02", true, HabitRecordStatus.DONE),
            createRecord("2026-09-03", true, HabitRecordStatus.DONE),
            createRecord("2026-09-04", true, HabitRecordStatus.DONE),
            createRecord("2026-09-05", false, HabitRecordStatus.SKIPPED)
        )

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)
        assertEquals(4, result.currentStreak)
        assertTrue(result.isFrozen)
        assertEquals(1, result.totalSkippedDays)
    }

    @Test
    fun testSkipsAloneDoNotEstablishStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Yoga",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1: SKIPPED, Sep 2: SKIPPED. No completions before.
        val records = listOf(
            createRecord("2026-09-01", false, HabitRecordStatus.SKIPPED),
            createRecord("2026-09-02", false, HabitRecordStatus.SKIPPED)
        )

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 2), utcZone)
        assertEquals(0, result.currentStreak)
        assertFalse(result.isFrozen)
        assertEquals(0, result.longestStreak)
    }

    @Test
    fun testPartialProgressBreaksStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Pushups",
            measurement = MeasurementType.Count(50, "reps"),
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1: DONE (50), Sep 2: PARTIAL (25), Sep 3: DONE (50)
        val records = listOf(
            createRecord("2026-09-01", true, HabitRecordStatus.DONE, actualValue = 50.0),
            createRecord("2026-09-02", false, HabitRecordStatus.PARTIAL, actualValue = 25.0),
            createRecord("2026-09-03", true, HabitRecordStatus.DONE, actualValue = 50.0)
        )

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 3), utcZone)
        // Sep 2 PARTIAL broke streak, so current streak on Sep 3 is 1
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
        assertEquals(2, result.totalCompletions)
    }

    @Test
    fun testExplicitMissedOnTodayRevokesMorningGraceImmediately() {
        val habit = Habit(
            id = "test_h",
            name = "Reading",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1: DONE, Sep 2: DONE. Today (Sep 3): explicitly marked MISSED
        val records = listOf(
            createRecord("2026-09-01", true, HabitRecordStatus.DONE),
            createRecord("2026-09-02", true, HabitRecordStatus.DONE),
            createRecord("2026-09-03", false, HabitRecordStatus.MISSED)
        )

        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 3), utcZone)
        // Morning grace revoked immediately
        assertEquals(0, result.currentStreak)
        assertEquals(2, result.longestStreak)
    }

    @Test
    fun testTemporalPausePreservesStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Meditation",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val pause = HabitPause(
            id = "pause1",
            habitId = "test_h",
            startDate = LocalDate.of(2026, 9, 3),
            endDate = LocalDate.of(2026, 9, 4),
            reason = "Vacation"
        )

        // Sep 1: DONE, Sep 2: DONE. Sep 3 & 4: PAUSED. Sep 5: DONE
        val records = listOf(
            createRecord("2026-09-01", true, HabitRecordStatus.DONE),
            createRecord("2026-09-02", true, HabitRecordStatus.DONE),
            createRecord("2026-09-05", true, HabitRecordStatus.DONE)
        )

        val result = useCase.execute(
            habit = habit,
            records = records,
            todayDate = LocalDate.of(2026, 9, 5),
            zoneId = utcZone,
            pauses = listOf(pause)
        )

        // Sep 1, 2, 5 are the 3 scheduled days; streak is 3
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
        assertEquals(3, result.totalCompletions)
        assertEquals(3, result.totalScheduledDays)
        assertEquals(100.0f, result.completionRate, 0.01f)
    }

    @Test
    fun testArchivedHabitHasZeroCurrentStreak() {
        val habit = Habit(
            id = "test_h",
            name = "Old Habit",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            isArchived = true,
            archivedAt = Instant.parse("2026-09-05T00:00:00Z"),
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val records = (1..5).map { createRecord("2026-09-0$it", true, HabitRecordStatus.DONE) }
        val result = useCase.execute(habit, records, LocalDate.of(2026, 9, 5), utcZone)

        // Decision 5.3.2: Archived habits have 0 current streak, but longest streak is preserved
        assertEquals(0, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }
}
