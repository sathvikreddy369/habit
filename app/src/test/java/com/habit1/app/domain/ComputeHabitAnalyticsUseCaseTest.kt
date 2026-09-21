package com.habit1.app.domain

import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ComputeHabitAnalyticsUseCaseTest {

    private val evaluateSchedule = EvaluateScheduleUseCase()
    private val calculateStreaks = CalculateStreaksUseCase(evaluateSchedule)
    private val useCase = ComputeHabitAnalyticsUseCase(evaluateSchedule, calculateStreaks)
    private val zoneId = ZoneId.of("UTC")

    private fun createHabit(
        id: String = "h1",
        name: String = "Test Habit",
        schedule: HabitSchedule = HabitSchedule.Daily,
        measurement: MeasurementType = MeasurementType.BooleanChoice,
        createdAt: Instant = Instant.parse("2026-09-01T00:00:00Z"),
        isPaused: Boolean = false,
        isArchived: Boolean = false
    ) = Habit(
        id = id,
        name = name,
        description = null,
        measurement = measurement,
        schedule = schedule,
        reminderTime = null,
        displayOrder = 0,
        isPaused = isPaused,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun createRecord(
        habitId: String = "h1",
        date: LocalDate,
        actualValue: Double = 1.0,
        targetValue: Double = 1.0,
        isCompleted: Boolean = true,
        unit: String? = null,
        measurementType: String = MeasurementType.BooleanChoice.TYPE_NAME
    ) = HabitRecord(
        id = "r_${habitId}_$date",
        habitId = habitId,
        date = date,
        actualValue = actualValue,
        targetValue = targetValue,
        measurementType = measurementType,
        unit = unit,
        isCompleted = isCompleted,
        notes = null,
        recordedAt = Instant.parse("${date}T10:00:00Z")
    )

    @Test
    fun testEmptyRange_singleDayNoRecords_allMissed() {
        val habit = createHabit()
        val today = LocalDate.of(2026, 9, 21)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10))

        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(1, summary.scheduledDays)
        assertEquals(0, summary.completedDays)
        assertEquals(1, summary.missedDays)
        assertEquals(0, summary.recordedIncompleteDays)
        assertEquals(0.0f, summary.completionRate, 0.01f)
        assertEquals(0, summary.currentStreak)
        assertNull(summary.quantitativeStats)
    }

    @Test
    fun testBooleanHabit_allCompleted() {
        val habit = createHabit()
        val today = LocalDate.of(2026, 9, 7)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7))

        val records = (1..7).map { day ->
            createRecord(date = LocalDate.of(2026, 9, day), isCompleted = true)
        }

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(7, summary.scheduledDays)
        assertEquals(7, summary.completedDays)
        assertEquals(0, summary.missedDays)
        assertEquals(0, summary.recordedIncompleteDays)
        assertEquals(100.0f, summary.completionRate, 0.01f)
        assertEquals(7, summary.currentStreak)
        assertEquals(7, summary.longestStreak)
        assertEquals(7, summary.dailyBreakdown.size)
        assertTrue(summary.dailyBreakdown.all { it.status is CalendarDayStatus.Completed })
    }

    @Test
    fun testRecordedIncomplete_distinctFromProjectedMissed() {
        val habit = createHabit()
        val today = LocalDate.of(2026, 9, 4)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))

        // Day 1: completed
        // Day 2: recorded incomplete (record exists with isCompleted = false)
        // Day 3: no record (projected missed)
        val records = listOf(
            createRecord(date = LocalDate.of(2026, 9, 1), isCompleted = true),
            createRecord(date = LocalDate.of(2026, 9, 2), actualValue = 0.0, isCompleted = false)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(3, summary.scheduledDays)
        assertEquals(1, summary.completedDays)
        assertEquals(1, summary.recordedIncompleteDays)
        assertEquals(1, summary.missedDays)
        assertEquals(33.33f, summary.completionRate, 0.01f)
    }

    @Test
    fun testSpecificDaysSchedule_restDaysNotMissed() {
        // Scheduled only Monday, Wednesday, Friday
        val habit = createHabit(
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        )
        // 2026-09-01 is Tuesday, 2026-09-07 is Monday
        val today = LocalDate.of(2026, 9, 8)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7))

        // Complete Wednesday (Sep 2) and Friday (Sep 4), miss Monday (Sep 7)
        val records = listOf(
            createRecord(date = LocalDate.of(2026, 9, 2), isCompleted = true),
            createRecord(date = LocalDate.of(2026, 9, 4), isCompleted = true)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        // Scheduled: Sep 2 (Wed), Sep 4 (Fri), Sep 7 (Mon) = 3 days
        assertEquals(3, summary.scheduledDays)
        assertEquals(2, summary.completedDays)
        assertEquals(1, summary.missedDays) // Sep 7
        assertEquals(4, summary.restDays)   // Sep 1 (Tue), Sep 3 (Thu), Sep 5 (Sat), Sep 6 (Sun)
        assertEquals(66.67f, summary.completionRate, 0.01f)
    }

    @Test
    fun testPreCreationAndFutureDates_notCountedAsMissed() {
        // Created on 2026-09-10
        val habit = createHabit(createdAt = Instant.parse("2026-09-10T00:00:00Z"))
        val today = LocalDate.of(2026, 9, 15)
        // Range covers pre-creation (Sep 5 to Sep 9) and future (Sep 16 to Sep 20)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 20))

        // Complete Sep 10, 11, 12, 13, 14, 15 (all 6 eligible days)
        val records = (10..15).map { day ->
            createRecord(date = LocalDate.of(2026, 9, day), isCompleted = true)
        }

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        // Scheduled dates in range: Sep 10 to Sep 20 = 11 days
        assertEquals(11, summary.scheduledDays)
        assertEquals(6, summary.completedDays)
        // Past scheduled days before today: Sep 10..14 (5 days) + today Sep 15 (completed) = 6 eligible days
        assertEquals(100.0f, summary.completionRate, 0.01f)
        assertEquals(0, summary.missedDays)

        // Verify pre-creation and future status in daily breakdown
        val preCreationCount = summary.dailyBreakdown.count { it.status is CalendarDayStatus.PreCreation }
        val futureCount = summary.dailyBreakdown.count { it.status is CalendarDayStatus.Future }
        assertEquals(5, preCreationCount) // Sep 5, 6, 7, 8, 9
        assertEquals(5, futureCount)      // Sep 16, 17, 18, 19, 20
    }

    @Test
    fun testPausedHabit_pausedDaysNotMissed() {
        val habit = createHabit(isPaused = true)
        val today = LocalDate.of(2026, 9, 10)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))

        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(0, summary.scheduledDays)
        assertEquals(0, summary.completedDays)
        assertEquals(0, summary.missedDays)
        assertEquals(0.0f, summary.completionRate, 0.01f)
        assertTrue(summary.dailyBreakdown.all { it.status is CalendarDayStatus.Paused })
    }

    @Test
    fun testCurrentDayGrace_incompleteTodayDoesNotDepressCompletionRate() {
        val habit = createHabit()
        val today = LocalDate.of(2026, 9, 3)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))

        // Completed Sep 1 and Sep 2. Sep 3 (today) has no record yet.
        val records = listOf(
            createRecord(date = LocalDate.of(2026, 9, 1), isCompleted = true),
            createRecord(date = LocalDate.of(2026, 9, 2), isCompleted = true)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        // Today is pending, so eligible past days = 2. Completions = 2.
        // Rate is 100%, not prematurely depressed to 66.7%!
        assertEquals(100.0f, summary.completionRate, 0.01f)
        assertEquals(2, summary.currentStreak) // Active streak intact!
    }

    @Test
    fun testCurrentDayCompleted_includedInCompletionRate() {
        val habit = createHabit()
        val today = LocalDate.of(2026, 9, 3)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))

        // All 3 days completed, including today
        val records = (1..3).map { day ->
            createRecord(date = LocalDate.of(2026, 9, day), isCompleted = true)
        }

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(3, summary.completedDays)
        assertEquals(100.0f, summary.completionRate, 0.01f)
        assertEquals(3, summary.currentStreak)
    }

    @Test
    fun testQuantitativeCount_preservesActualValuesAndComputesAverages() {
        val measurement = MeasurementType.Count(target = 50, unit = "reps")
        val habit = createHabit(measurement = measurement)
        val today = LocalDate.of(2026, 9, 5)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4))

        // Sep 1: 50 reps (completed)
        // Sep 2: 70 reps (exceeded target! Must preserve 70, not clamped)
        // Sep 3: 30 reps (incomplete attempt)
        // Sep 4: missed (no record)
        val records = listOf(
            createRecord(date = LocalDate.of(2026, 9, 1), actualValue = 50.0, targetValue = 50.0, isCompleted = true, unit = "reps", measurementType = MeasurementType.Count.TYPE_NAME),
            createRecord(date = LocalDate.of(2026, 9, 2), actualValue = 70.0, targetValue = 50.0, isCompleted = true, unit = "reps", measurementType = MeasurementType.Count.TYPE_NAME),
            createRecord(date = LocalDate.of(2026, 9, 3), actualValue = 30.0, targetValue = 50.0, isCompleted = false, unit = "reps", measurementType = MeasurementType.Count.TYPE_NAME)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        val stats = summary.quantitativeStats
        assertNotNull(stats)
        assertEquals(150.0, stats!!.totalActualValue, 0.01) // 50 + 70 + 30
        assertEquals(60.0, stats.averageOnCompletedDays!!, 0.01) // (50 + 70) / 2
        assertEquals(50.0, stats.averageOnRecordedDays!!, 0.01) // 150 / 3
        assertEquals(3, stats.recordedDaysCount)
        assertEquals(2, stats.completedDaysCount)
        assertEquals(50.0f, stats.targetAchievementRate, 0.01f) // 2 of 4 scheduled
        assertEquals(66.67f, stats.successRateOnRecordedDays!!, 0.01f) // 2 of 3 recorded

        assertEquals(150.0, summary.totalActualValue, 0.01)
        assertEquals(50.0, summary.averageActualValue!!, 0.01)
    }

    @Test
    fun testHistoricalIntegrity_scheduleChangeDoesNotReinterpretFactualRecords() {
        // Habit schedule changed to Weekends only
        val habit = createHabit(
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        )
        val today = LocalDate.of(2026, 9, 10)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7))

        // But user factually completed a session on Tuesday (Sep 1) and Thursday (Sep 3)
        val records = listOf(
            createRecord(date = LocalDate.of(2026, 9, 1), isCompleted = true),
            createRecord(date = LocalDate.of(2026, 9, 3), isCompleted = true)
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        // Factual records are recognized and completed
        assertEquals(2, summary.completedDays)
        assertTrue(summary.dailyBreakdown.first { it.date == LocalDate.of(2026, 9, 1) }.status is CalendarDayStatus.Completed)
        assertTrue(summary.dailyBreakdown.first { it.date == LocalDate.of(2026, 9, 3) }.status is CalendarDayStatus.Completed)
    }

    @Test
    fun testStreakConsistency_agreesWithCalculateStreaksUseCase() {
        val habit = createHabit()
        val today = LocalDate.of(2026, 9, 10)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))

        val records = (1..10).map { day ->
            createRecord(date = LocalDate.of(2026, 9, day), isCompleted = day != 5) // Day 5 missed
        }

        val expectedStreak = calculateStreaks.execute(
            habit = habit,
            records = records,
            todayDate = today,
            zoneId = zoneId,
            startDate = range.startDate,
            endDate = range.endDate
        )

        val summary = useCase.execute(
            habit = habit,
            records = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(expectedStreak.currentStreak, summary.currentStreak)
        assertEquals(expectedStreak.longestStreak, summary.longestStreak)
        assertEquals(5, summary.currentStreak) // 6, 7, 8, 9, 10 = 5 days
    }

    @Test
    fun testExecuteWithEntities_matchesDomainRecords() {
        val habit = createHabit()
        val today = LocalDate.of(2026, 9, 5)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4))

        val entities = listOf(
            HabitRecordEntity(
                id = "r1",
                habitId = "h1",
                date = "2026-09-01",
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = MeasurementType.BooleanChoice.TYPE_NAME,
                unit = null,
                isCompleted = true,
                notes = null,
                recordedAt = 1000L
            ),
            HabitRecordEntity(
                id = "r2",
                habitId = "h1",
                date = "2026-09-02",
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = MeasurementType.BooleanChoice.TYPE_NAME,
                unit = null,
                isCompleted = true,
                notes = null,
                recordedAt = 2000L
            )
        )

        val summary = useCase.executeWithEntities(
            habit = habit,
            recordEntities = entities,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(2, summary.completedDays)
        assertEquals(4, summary.scheduledDays)
        assertEquals(2, summary.missedDays)
        assertEquals(50.0f, summary.completionRate, 0.01f)
    }
}
