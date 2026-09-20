package com.habit1.app.domain

import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EvaluateHabitHistoryUseCaseTest {

    private val scheduleUseCase = EvaluateScheduleUseCase()
    private val streakUseCase = CalculateStreaksUseCase(scheduleUseCase)
    private val useCase = EvaluateHabitHistoryUseCase(scheduleUseCase, streakUseCase)

    private val zoneId = ZoneId.of("UTC")
    private val baseInstant = Instant.parse("2026-09-01T00:00:00Z")

    private fun createRecord(
        dateStr: String,
        isCompleted: Boolean,
        actualValue: Double = if (isCompleted) 1.0 else 0.0,
        targetValue: Double = 1.0,
        unit: String? = null,
        measurementType: String = "BOOLEAN"
    ): HabitRecord {
        return HabitRecord(
            id = "rec_$dateStr",
            habitId = "test_h",
            date = LocalDate.parse(dateStr),
            actualValue = actualValue,
            targetValue = targetValue,
            unit = unit,
            measurementType = measurementType,
            isCompleted = isCompleted,
            recordedAt = Instant.parse("${dateStr}T12:00:00Z")
        )
    }

    @Test
    fun testEmptyHistory_noDivisionByZero() {
        val habit = Habit(
            id = "test_h",
            name = "Water",
            measurement = MeasurementType.Quantity(target = 8.0, unit = "glasses"),
            schedule = HabitSchedule.Daily,
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-09-01T00:00:00Z")
        )

        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 10),
            todayDate = LocalDate.of(2026, 9, 10),
            zoneId = zoneId
        )

        assertEquals(0, summary.streakResult.totalCompletions)
        assertEquals(0, summary.totalRecordedDays)
        assertEquals(10, summary.streakResult.totalScheduledDays)
        assertEquals(0.0f, summary.streakResult.completionRate, 0.001f)
        assertEquals(null, summary.averageOnCompletedDays)
        assertEquals(null, summary.averageOnRecordedDays)
        assertEquals(0, summary.streakResult.currentStreak)
        assertEquals(0, summary.streakResult.longestStreak)
        assertEquals(10, summary.historyDays.size)
        // All 10 unrecorded days should be ProjectedMissed
        assertTrue(summary.historyDays.all { it.status is CalendarDayStatus.ProjectedMissed })
    }

    @Test
    fun testStreakReuseConsistency_matchesCalculateStreaksUseCase() {
        val habit = Habit(
            id = "test_h",
            name = "Reading",
            measurement = MeasurementType.Count(target = 20, unit = "pages"),
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val records = listOf(
            createRecord("2026-09-01", true, 20.0, 20.0, "pages", "COUNT"),
            createRecord("2026-09-02", true, 25.0, 20.0, "pages", "COUNT"),
            createRecord("2026-09-03", false, 10.0, 20.0, "pages", "COUNT"), // incomplete
            createRecord("2026-09-04", true, 20.0, 20.0, "pages", "COUNT"),
            createRecord("2026-09-05", true, 20.0, 20.0, "pages", "COUNT")
        )

        val today = LocalDate.of(2026, 9, 5)
        val canonicalStreak = streakUseCase.execute(habit, records, today, zoneId)

        val historySummary = useCase.execute(
            habit = habit,
            records = records,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = today,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(canonicalStreak.currentStreak, historySummary.streakResult.currentStreak)
        assertEquals(canonicalStreak.longestStreak, historySummary.streakResult.longestStreak)
    }

    @Test
    fun testRecordedIncompleteVsProjectedMissed() {
        val habit = Habit(
            id = "test_h",
            name = "Pushups",
            measurement = MeasurementType.Count(target = 50, unit = "reps"),
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1: completed
        // Sep 2: recorded incomplete (did 20 out of 50)
        // Sep 3: unrecorded (missed)
        val records = listOf(
            createRecord("2026-09-01", true, 50.0, 50.0, "reps", "COUNT"),
            createRecord("2026-09-02", false, 20.0, 50.0, "reps", "COUNT")
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 3),
            todayDate = LocalDate.of(2026, 9, 3),
            zoneId = zoneId
        )

        assertEquals(1, summary.completedDaysCount)
        assertEquals(2, summary.totalRecordedDays)
        assertEquals(1, summary.recordedIncompleteDaysCount)
        assertEquals(3, summary.streakResult.totalScheduledDays)

        // Day 1 is Completed
        val day1 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 1) }
        assertTrue(day1?.status is CalendarDayStatus.Completed)

        // Day 2 is RecordedIncomplete
        val day2 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 2) }
        assertTrue(day2?.status is CalendarDayStatus.RecordedIncomplete)
        val incompleteStatus = day2?.status as CalendarDayStatus.RecordedIncomplete
        assertEquals(20.0, incompleteStatus.actualValue, 0.001)
        assertEquals(50.0, incompleteStatus.targetValue, 0.001)

        // Day 3 is ProjectedMissed
        val day3 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 3) }
        assertTrue(day3?.status is CalendarDayStatus.ProjectedMissed)
    }

    @Test
    fun testScheduleChangeWithOldRecords_completionPreservedEvenIfNotCurrentlyScheduled() {
        // Suppose habit's current schedule is Tuesday and Thursday only.
        // But historically on Wednesday Sep 2, 2026, user recorded a completion!
        // (Sep 2, 2026 was a Wednesday).
        val habit = Habit(
            id = "test_h",
            name = "Gym",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val wednesdayRecord = createRecord("2026-09-02", true) // Wednesday Sep 2

        val summary = useCase.execute(
            habit = habit,
            records = listOf(wednesdayRecord),
            startDate = LocalDate.of(2026, 9, 1), // Tuesday
            endDate = LocalDate.of(2026, 9, 3),   // Thursday
            todayDate = LocalDate.of(2026, 9, 3),
            zoneId = zoneId
        )

        // Wednesday Sep 2 MUST be CalendarDayStatus.Completed because factual record exists
        val wednesdayDay = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 2) }
        assertNotNull(wednesdayDay)
        assertTrue("Factual completion must be honored even if not currently scheduled", wednesdayDay?.status is CalendarDayStatus.Completed)

        // Tuesday Sep 1 (no record) -> ProjectedMissed (because Tuesday is in current schedule)
        val tuesdayDay = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 1) }
        assertTrue(tuesdayDay?.status is CalendarDayStatus.ProjectedMissed)

        // Thursday Sep 3 (no record, is today) -> ProjectedMissed
        val thursdayDay = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 3) }
        assertTrue(thursdayDay?.status is CalendarDayStatus.ProjectedMissed)
    }

    @Test
    fun testScheduleChangeWithUnrecordedDates_projectedClassification() {
        val habit = Habit(
            id = "test_h",
            name = "Yoga",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            createdAt = Instant.parse("2026-08-30T00:00:00Z"),
            updatedAt = Instant.parse("2026-09-01T00:00:00Z")
        )

        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            startDate = LocalDate.of(2026, 8, 31), // Monday
            endDate = LocalDate.of(2026, 9, 1),   // Tuesday
            todayDate = LocalDate.of(2026, 9, 1),
            zoneId = zoneId
        )

        val monday = summary.historyDays.find { it.date == LocalDate.of(2026, 8, 31) }
        assertTrue("Unrecorded day not in current schedule is projected rest", monday?.status is CalendarDayStatus.ProjectedRest)

        val tuesday = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 1) }
        assertTrue("Unrecorded day in current schedule is projected missed", tuesday?.status is CalendarDayStatus.ProjectedMissed)
    }

    @Test
    fun testCreationDateBoundary_preCreationDates() {
        val habit = Habit(
            id = "test_h",
            name = "Coding",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = Instant.parse("2026-09-03T10:00:00Z"),
            updatedAt = Instant.parse("2026-09-03T10:00:00Z")
        )

        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 5),
            todayDate = LocalDate.of(2026, 9, 5),
            zoneId = zoneId
        )

        val sep1 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 1) }
        val sep2 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 2) }
        val sep3 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 3) }

        assertTrue(sep1?.status is CalendarDayStatus.PreCreation)
        assertTrue(sep2?.status is CalendarDayStatus.PreCreation)
        assertTrue(sep3?.status is CalendarDayStatus.ProjectedMissed)
    }

    @Test
    fun testFutureDates() {
        val habit = Habit(
            id = "test_h",
            name = "Meditation",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 5),
            todayDate = LocalDate.of(2026, 9, 2),
            zoneId = zoneId
        )

        val sep3 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 3) }
        val sep4 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 4) }
        val sep5 = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 5) }

        assertTrue(sep3?.status is CalendarDayStatus.Future)
        assertTrue(sep4?.status is CalendarDayStatus.Future)
        assertTrue(sep5?.status is CalendarDayStatus.Future)
    }

    @Test
    fun testPausedPeriods_doNotCountAsMissed() {
        val habit = Habit(
            id = "test_h",
            name = "Running",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            isPaused = true,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 5),
            todayDate = LocalDate.of(2026, 9, 5),
            zoneId = zoneId
        )

        assertEquals(0, summary.streakResult.totalScheduledDays)
        assertEquals(0.0f, summary.streakResult.completionRate, 0.001f)
        assertTrue(summary.historyDays.all { it.status is CalendarDayStatus.Paused })
    }

    @Test
    fun testArchivedHabits_evaluateHistoryProperly() {
        val habit = Habit(
            id = "test_h",
            name = "Old Habit",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            isArchived = true,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        val records = listOf(
            createRecord("2026-09-01", true),
            createRecord("2026-09-02", true)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 3),
            todayDate = LocalDate.of(2026, 9, 3),
            zoneId = zoneId
        )

        assertEquals(2, summary.completedDaysCount)
        assertEquals(2, summary.totalRecordedDays)
        assertEquals(2, summary.streakResult.longestStreak)
    }

    @Test
    fun testQuantitativeAveragesAndSnapshotIntegrity() {
        val habit = Habit(
            id = "test_h",
            name = "Water",
            measurement = MeasurementType.Quantity(target = 100.0, unit = "ml"),
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )

        // Sep 1: target 100, actual 120 (completed)
        // Sep 2: target 100, actual 100 (completed)
        // Sep 3: target 100, actual 50 (incomplete attempt)
        val records = listOf(
            createRecord("2026-09-01", true, actualValue = 120.0, targetValue = 100.0, unit = "ml", measurementType = "QUANTITY"),
            createRecord("2026-09-02", true, actualValue = 100.0, targetValue = 100.0, unit = "ml", measurementType = "QUANTITY"),
            createRecord("2026-09-03", false, actualValue = 50.0, targetValue = 100.0, unit = "ml", measurementType = "QUANTITY")
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 3),
            todayDate = LocalDate.of(2026, 9, 3),
            zoneId = zoneId
        )

        assertEquals(2, summary.completedDaysCount)
        assertEquals(3, summary.totalRecordedDays)
        assertEquals(1, summary.recordedIncompleteDaysCount)
        assertEquals(110.0, summary.averageOnCompletedDays!!, 0.001)
        assertEquals(90.0, summary.averageOnRecordedDays!!, 0.001)

        // Historical records retain their snapshot unit
        val completedDay1 = summary.historyDays[0].status as CalendarDayStatus.Completed
        assertEquals("ml", completedDay1.unit)
        assertEquals(120.0, completedDay1.actualValue, 0.001)
    }

    @Test
    fun testLeapYearDateBoundary() {
        val leapCreatedAt = Instant.parse("2024-02-27T00:00:00Z")
        val habit = Habit(
            id = "test_h",
            name = "Leap Habit",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = leapCreatedAt,
            updatedAt = leapCreatedAt
        )

        val records = listOf(
            createRecord("2024-02-28", true),
            createRecord("2024-02-29", true),
            createRecord("2024-03-01", true)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            startDate = LocalDate.of(2024, 2, 28),
            endDate = LocalDate.of(2024, 3, 1),
            todayDate = LocalDate.of(2024, 3, 1),
            zoneId = zoneId
        )

        assertEquals(3, summary.completedDaysCount)
        assertEquals(3, summary.historyDays.size)
        assertTrue(summary.historyDays.any { it.date == LocalDate.of(2024, 2, 29) })
        assertEquals(3, summary.streakResult.currentStreak)
    }

    @Test
    fun testYearBoundaryTransition() {
        val yearEndCreatedAt = Instant.parse("2025-12-30T00:00:00Z")
        val habit = Habit(
            id = "test_h",
            name = "New Year Habit",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = yearEndCreatedAt,
            updatedAt = yearEndCreatedAt
        )

        val records = listOf(
            createRecord("2025-12-31", true),
            createRecord("2026-01-01", true)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            startDate = LocalDate.of(2025, 12, 31),
            endDate = LocalDate.of(2026, 1, 1),
            todayDate = LocalDate.of(2026, 1, 1),
            zoneId = zoneId
        )

        assertEquals(2, summary.completedDaysCount)
        assertEquals(2, summary.streakResult.currentStreak)
        assertEquals(2, summary.historyDays.size)
    }
}
