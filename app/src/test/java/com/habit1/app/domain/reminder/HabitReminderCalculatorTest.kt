package com.habit1.app.domain.reminder

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class HabitReminderCalculatorTest {

    private val calculator = HabitReminderCalculator(EvaluateScheduleUseCase())
    private val utcZone = ZoneId.of("UTC")
    private val baseInstant = Instant.parse("2026-09-01T00:00:00Z")

    private fun createHabit(
        schedule: HabitSchedule,
        reminderTime: LocalTime? = LocalTime.of(8, 30),
        isPaused: Boolean = false,
        isArchived: Boolean = false,
        createdAt: Instant = baseInstant
    ): Habit {
        return Habit(
            id = "test_habit",
            name = "Morning Routine",
            measurement = MeasurementType.BooleanChoice,
            schedule = schedule,
            reminderTime = reminderTime,
            isPaused = isPaused,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    @Test
    fun testNullReminderTime_returnsNull() {
        val habit = createHabit(HabitSchedule.Daily, reminderTime = null)
        val next = calculator.calculateNextReminder(habit, baseInstant, utcZone)
        assertNull(next)
    }

    @Test
    fun testPausedOrArchivedHabit_returnsNull() {
        val pausedHabit = createHabit(HabitSchedule.Daily, isPaused = true)
        val archivedHabit = createHabit(HabitSchedule.Daily, isArchived = true)

        assertNull(calculator.calculateNextReminder(pausedHabit, baseInstant, utcZone))
        assertNull(calculator.calculateNextReminder(archivedHabit, baseInstant, utcZone))
    }

    @Test
    fun testDailyHabit_beforeReminderTimeToday_triggersToday() {
        val habit = createHabit(HabitSchedule.Daily, reminderTime = LocalTime.of(8, 30))
        // Reference time: 07:00 UTC on 2026-09-01
        val fromInstant = Instant.parse("2026-09-01T07:00:00Z")

        val next = calculator.calculateNextReminder(habit, fromInstant, utcZone)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 9, 1), next?.toLocalDate())
        assertEquals(LocalTime.of(8, 30), next?.toLocalTime())
    }

    @Test
    fun testDailyHabit_afterReminderTimeToday_triggersTomorrow() {
        val habit = createHabit(HabitSchedule.Daily, reminderTime = LocalTime.of(8, 30))
        // Reference time: 09:00 UTC on 2026-09-01
        val fromInstant = Instant.parse("2026-09-01T09:00:00Z")

        val next = calculator.calculateNextReminder(habit, fromInstant, utcZone)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 9, 2), next?.toLocalDate())
        assertEquals(LocalTime.of(8, 30), next?.toLocalTime())
    }

    @Test
    fun testSpecificDaysHabit_triggersOnNextScheduledDay() {
        // Mon (1), Wed (3), Fri (5)
        val habit = createHabit(
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            reminderTime = LocalTime.of(18, 0)
        )

        // 2026-09-01 is Tuesday. Next scheduled is Wednesday (2026-09-02)
        val tuesdayInstant = Instant.parse("2026-09-01T10:00:00Z")
        val nextFromTuesday = calculator.calculateNextReminder(habit, tuesdayInstant, utcZone)
        assertNotNull(nextFromTuesday)
        assertEquals(LocalDate.of(2026, 9, 2), nextFromTuesday?.toLocalDate())
        assertEquals(LocalTime.of(18, 0), nextFromTuesday?.toLocalTime())

        // 2026-09-04 is Friday at 19:00 (after reminder time). Next scheduled is Monday (2026-09-07)
        val fridayAfterReminder = Instant.parse("2026-09-04T19:00:00Z")
        val nextFromFriday = calculator.calculateNextReminder(habit, fridayAfterReminder, utcZone)
        assertNotNull(nextFromFriday)
        assertEquals(LocalDate.of(2026, 9, 7), nextFromFriday?.toLocalDate())
        assertEquals(LocalTime.of(18, 0), nextFromFriday?.toLocalTime())
    }

    @Test
    fun testIntervalHabit_everyThreeDays() {
        // Anchor: 2026-09-01, every 3 days (Sep 1, Sep 4, Sep 7)
        val habit = createHabit(
            schedule = HabitSchedule.Interval(everyNDays = 3, anchorDate = LocalDate.of(2026, 9, 1)),
            reminderTime = LocalTime.of(20, 0)
        )

        // On Sep 2 (day 1, not scheduled) -> next is Sep 4
        val fromSep2 = Instant.parse("2026-09-02T12:00:00Z")
        val next = calculator.calculateNextReminder(habit, fromSep2, utcZone)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 9, 4), next?.toLocalDate())
        assertEquals(LocalTime.of(20, 0), next?.toLocalTime())
    }

    @Test
    fun testYearBoundaryTransition() {
        val habit = createHabit(HabitSchedule.Daily, reminderTime = LocalTime.of(9, 0))
        // Dec 31 at 10:00 (after reminder time) -> next is Jan 1 of new year
        val newYearsEve = Instant.parse("2026-12-31T10:00:00Z")
        val next = calculator.calculateNextReminder(habit, newYearsEve, utcZone)
        assertNotNull(next)
        assertEquals(LocalDate.of(2027, 1, 1), next?.toLocalDate())
        assertEquals(LocalTime.of(9, 0), next?.toLocalTime())
    }

    @Test
    fun testLeapYearTransition() {
        // 2024 is a leap year
        val leapCreatedAt = Instant.parse("2024-01-01T00:00:00Z")
        val habit = createHabit(HabitSchedule.Daily, reminderTime = LocalTime.of(7, 0), createdAt = leapCreatedAt)

        // Feb 28, 2024 at 08:00 (after reminder time) -> next is Feb 29 (Leap Day)
        val feb28 = Instant.parse("2024-02-28T08:00:00Z")
        val next = calculator.calculateNextReminder(habit, feb28, utcZone)
        assertNotNull(next)
        assertEquals(LocalDate.of(2024, 2, 29), next?.toLocalDate())
        assertEquals(LocalTime.of(7, 0), next?.toLocalTime())
    }

    @Test
    fun testTimezoneAwareTriggerCalculation() {
        val tokyoZone = ZoneId.of("Asia/Tokyo") // UTC+9
        val habit = createHabit(HabitSchedule.Daily, reminderTime = LocalTime.of(8, 0))

        // When UTC is 2026-09-01T22:00:00Z, Tokyo is 2026-09-02T07:00:00 (+9h)
        val fromInstant = Instant.parse("2026-09-01T22:00:00Z")
        val nextTokyo = calculator.calculateNextReminder(habit, fromInstant, tokyoZone)

        assertNotNull(nextTokyo)
        // In Tokyo it's 7am on Sep 2, so the 8am reminder on Sep 2 hasn't fired yet!
        assertEquals(LocalDate.of(2026, 9, 2), nextTokyo?.toLocalDate())
        assertEquals(LocalTime.of(8, 0), nextTokyo?.toLocalTime())
    }

    @Test
    fun testTimezoneChangeRecalculation_adjustsAlarmInstantToLocalCivilTime() {
        val habit = createHabit(HabitSchedule.Daily, reminderTime = LocalTime.of(8, 0))
        val anchorInstant = Instant.parse("2026-09-02T00:00:00Z")

        val tokyoZone = ZoneId.of("Asia/Tokyo") // UTC+9
        val newYorkZone = ZoneId.of("America/New_York") // UTC-4 (EDT in Sep)

        // In Tokyo, 08:00 AM on Sep 2 is 2026-09-01T23:00:00Z
        // At anchorInstant (00:00 UTC = 09:00 Tokyo), today's 08:00 already passed, so next is Sep 3 08:00 JST = Sep 2 23:00 UTC
        val tokyoNext = calculator.calculateNextReminder(habit, anchorInstant, tokyoZone)
        assertNotNull(tokyoNext)
        assertEquals(LocalDate.of(2026, 9, 3), tokyoNext?.toLocalDate())
        assertEquals(LocalTime.of(8, 0), tokyoNext?.toLocalTime())
        assertEquals(Instant.parse("2026-09-02T23:00:00Z"), tokyoNext?.toInstant())

        // In New York, anchorInstant (00:00 UTC = 20:00 EDT Sep 1). Next 08:00 AM is Sep 2 08:00 EDT = Sep 2 12:00 UTC
        val newYorkNext = calculator.calculateNextReminder(habit, anchorInstant, newYorkZone)
        assertNotNull(newYorkNext)
        assertEquals(LocalDate.of(2026, 9, 2), newYorkNext?.toLocalDate())
        assertEquals(LocalTime.of(8, 0), newYorkNext?.toLocalTime())
        assertEquals(Instant.parse("2026-09-02T12:00:00Z"), newYorkNext?.toInstant())
    }
}
