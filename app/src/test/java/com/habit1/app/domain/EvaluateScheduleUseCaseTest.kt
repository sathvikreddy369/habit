package com.habit1.app.domain

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitPause
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.HabitScheduleVersion
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EvaluateScheduleUseCaseTest {

    private val useCase = EvaluateScheduleUseCase()
    private val utcZone = ZoneId.of("UTC")
    private val baseInstant = Instant.parse("2026-01-01T00:00:00Z")

    private fun createHabit(
        schedule: HabitSchedule,
        isPaused: Boolean = false,
        isArchived: Boolean = false,
        createdAt: Instant = baseInstant
    ): Habit {
        return Habit(
            id = "test_h",
            name = "Test Habit",
            measurement = MeasurementType.BooleanChoice,
            schedule = schedule,
            isPaused = isPaused,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    @Test
    fun testDailyScheduleEveryDay() {
        val habit = createHabit(HabitSchedule.Daily)
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 1, 1), utcZone))
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 6, 15), utcZone))
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 12, 31), utcZone))
    }

    @Test
    fun testDailyScheduleLeapYearTransition() {
        // 2024 is a leap year; 2026 is not.
        val leapCreatedAt = Instant.parse("2024-01-01T00:00:00Z")
        val habit = createHabit(HabitSchedule.Daily, createdAt = leapCreatedAt)

        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2024, 2, 28), utcZone))
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2024, 2, 29), utcZone)) // Leap day
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2024, 3, 1), utcZone))
    }

    @Test
    fun testSpecificDaysSchedule() {
        // Monday (1), Wednesday (3), Friday (5)
        val habit = createHabit(
            HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        )

        // 2026-09-21 is Monday
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 21), utcZone))
        // 2026-09-22 is Tuesday -> false
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 22), utcZone))
        // 2026-09-23 is Wednesday -> true
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 23), utcZone))
        // 2026-09-24 is Thursday -> false
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 24), utcZone))
        // 2026-09-25 is Friday -> true
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 25), utcZone))
        // 2026-09-26 is Saturday -> false
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 26), utcZone))
        // 2026-09-27 is Sunday -> false
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 27), utcZone))
    }

    @Test
    fun testIntervalScheduleAcrossMonthAndYearBoundaries() {
        // Every 3 days starting 2026-01-01
        val anchor = LocalDate.of(2026, 1, 1)
        val habit = createHabit(HabitSchedule.Interval(everyNDays = 3, anchorDate = anchor))

        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 1, 1), utcZone)) // Day 0
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 1, 2), utcZone)) // Day 1
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 1, 3), utcZone)) // Day 2
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 1, 4), utcZone)) // Day 3
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 1, 31), utcZone)) // Day 30 -> 30 % 3 == 0

        // Month boundary: Jan 31 (day 30) -> Feb 3 (day 33) -> true
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 2, 3), utcZone))
    }

    @Test
    fun testPausedAndArchivedHabitsNeverScheduled() {
        val pausedHabit = createHabit(HabitSchedule.Daily, isPaused = true)
        val archivedHabit = createHabit(HabitSchedule.Daily, isArchived = true)

        assertFalse(useCase.isScheduledOn(pausedHabit, LocalDate.of(2026, 9, 20), utcZone))
        assertFalse(useCase.isScheduledOn(archivedHabit, LocalDate.of(2026, 9, 20), utcZone))
    }

    @Test
    fun testDatesBeforeCreationDateNotScheduled() {
        // Created on 2026-06-01
        val createdAt = Instant.parse("2026-06-01T00:00:00Z")
        val habit = createHabit(HabitSchedule.Daily, createdAt = createdAt)

        // Day before creation
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 5, 31), utcZone))
        // Day of creation
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 6, 1), utcZone))
    }

    @Test
    fun testGetScheduledDatesInRange() {
        val habit = createHabit(
            HabitSchedule.SpecificDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        )
        // 2026-09-14 (Mon) to 2026-09-20 (Sun)
        val range = useCase.getScheduledDatesInRange(
            habit,
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 9, 20),
            utcZone
        )
        assertEquals(2, range.size)
        assertEquals(LocalDate.of(2026, 9, 19), range[0]) // Saturday
        assertEquals(LocalDate.of(2026, 9, 20), range[1]) // Sunday
    }

    @Test
    fun testTemporalPausesInclusiveSemantics() {
        val habit = createHabit(HabitSchedule.Daily)
        val pause = HabitPause(
            id = "p1",
            habitId = "test_h",
            startDate = LocalDate.of(2026, 9, 10),
            endDate = LocalDate.of(2026, 9, 12),
            reason = "Vacation"
        )
        val pauses = listOf(pause)

        // Day before pause -> scheduled
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 9), utcZone, pauses))
        assertFalse(useCase.isDayPaused(habit, LocalDate.of(2026, 9, 9), pauses))

        // First day of pause (inclusive) -> paused and not scheduled
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 10), utcZone, pauses))
        assertTrue(useCase.isDayPaused(habit, LocalDate.of(2026, 9, 10), pauses))

        // Middle day of pause -> paused
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 11), utcZone, pauses))
        assertTrue(useCase.isDayPaused(habit, LocalDate.of(2026, 9, 11), pauses))

        // Last day of pause (inclusive) -> paused
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 12), utcZone, pauses))
        assertTrue(useCase.isDayPaused(habit, LocalDate.of(2026, 9, 12), pauses))

        // Day after pause resumes -> scheduled and not paused
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 13), utcZone, pauses))
        assertFalse(useCase.isDayPaused(habit, LocalDate.of(2026, 9, 13), pauses))
    }

    @Test
    fun testScheduleVersionsPreserveHistoricalTruth() {
        // Version 1 (Daily): from 2026-09-01 to 2026-09-09
        val v1 = HabitScheduleVersion(
            id = "v1",
            habitId = "test_h",
            schedule = HabitSchedule.Daily,
            effectiveFrom = LocalDate.of(2026, 9, 1),
            effectiveTo = LocalDate.of(2026, 9, 9)
        )
        // Version 2 (Weekends only): from 2026-09-10 onwards
        val v2 = HabitScheduleVersion(
            id = "v2",
            habitId = "test_h",
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            effectiveFrom = LocalDate.of(2026, 9, 10),
            effectiveTo = null
        )
        val versions = listOf(v1, v2)
        val habit = createHabit(schedule = HabitSchedule.Daily)

        // 2026-09-08 is Tuesday -> under v1 (Daily), so scheduled
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 8), utcZone, scheduleVersions = versions))

        // 2026-09-15 is Tuesday -> under v2 (Weekends), so NOT scheduled
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 15), utcZone, scheduleVersions = versions))

        // 2026-09-19 is Saturday -> under v2 (Weekends), so scheduled
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 19), utcZone, scheduleVersions = versions))
    }

    @Test
    fun testArchivedAtBoundarySuppressesOccurrencesAfterArchival() {
        // Habit created on Sep 1, archived on Sep 10
        val archivedInstant = Instant.parse("2026-09-10T15:00:00Z")
        val habit = Habit(
            id = "test_h",
            name = "Archived Habit",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            isArchived = true,
            archivedAt = archivedInstant,
            createdAt = baseInstant,
            updatedAt = archivedInstant
        )

        // On or before archive date (Sep 10): scheduled
        assertTrue(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 10), utcZone))

        // After archive date (Sep 11): NOT scheduled
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 11), utcZone))
        assertFalse(useCase.isScheduledOn(habit, LocalDate.of(2026, 9, 12), utcZone))
    }
}
