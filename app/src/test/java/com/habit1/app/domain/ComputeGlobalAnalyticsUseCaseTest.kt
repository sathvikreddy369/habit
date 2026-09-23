package com.habit1.app.domain

import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.ComputeGlobalAnalyticsUseCase
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ComputeGlobalAnalyticsUseCaseTest {

    private val evaluateSchedule = EvaluateScheduleUseCase()
    private val calculateStreaks = CalculateStreaksUseCase(evaluateSchedule)
    private val computeHabitAnalytics = ComputeHabitAnalyticsUseCase(evaluateSchedule, calculateStreaks)
    private val useCase = ComputeGlobalAnalyticsUseCase(computeHabitAnalytics)
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
    fun testEmptyHabitsList_returnsZeroRates() {
        val today = LocalDate.of(2026, 9, 21)
        val range = AnalyticsRange.ofDaysEndingAt(today, 7)

        val summary = useCase.execute(
            habits = emptyList(),
            recordsByHabit = emptyMap(),
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(0.0f, summary.globalCompletionRate, 0.001f)
        assertEquals(0, summary.totalCompletedDays)
        assertEquals(0, summary.totalScheduledDays)
        assertEquals(0, summary.activeHabitsCount)
        assertTrue(summary.streakLeaders.isEmpty())
        assertTrue(summary.habitPerformances.isEmpty())
    }

    @Test
    fun testMultipleActiveHabits_calculatesWeightedGlobalRateAndTotals() {
        val today = LocalDate.of(2026, 9, 14)
        val range = AnalyticsRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14)) // 5 days

        val habit1 = createHabit(id = "h1", name = "Habit 1")
        val habit2 = createHabit(id = "h2", name = "Habit 2")

        // Habit 1 completed 4 out of 5 days
        val recordsH1 = listOf(
            createRecord("h1", LocalDate.of(2026, 9, 10), isCompleted = true),
            createRecord("h1", LocalDate.of(2026, 9, 11), isCompleted = true),
            createRecord("h1", LocalDate.of(2026, 9, 12), isCompleted = true),
            createRecord("h1", LocalDate.of(2026, 9, 13), isCompleted = true)
        )

        // Habit 2 completed 2 out of 5 days
        val recordsH2 = listOf(
            createRecord("h2", LocalDate.of(2026, 9, 10), isCompleted = true),
            createRecord("h2", LocalDate.of(2026, 9, 11), isCompleted = true)
        )

        val summary = useCase.execute(
            habits = listOf(habit1, habit2),
            recordsByHabit = mapOf("h1" to recordsH1, "h2" to recordsH2),
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(2, summary.activeHabitsCount)
        assertEquals(6, summary.totalCompletedDays) // 4 + 2
        assertEquals(10, summary.totalScheduledDays) // 5 per habit in 5-day range
        // Rate = 6 completed / 8 eligible days = 75.0%
        assertEquals(75.0f, summary.globalCompletionRate, 0.001f)
        assertEquals(2, summary.habitPerformances.size)
    }

    @Test
    fun testAsymmetricSchedules_calculatesStrictWeightedOccurrenceRatio() {
        val today = LocalDate.of(2026, 9, 21) // Monday
        val range = AnalyticsRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)) // 7 past days (Mon-Sun)

        // Habit 1: Daily habit, completed all 7 days
        val dailyHabit = createHabit(id = "h_daily", name = "Daily Exercise", schedule = HabitSchedule.Daily)
        val dailyRecords = (14..20).map { day ->
            createRecord("h_daily", LocalDate.of(2026, 9, day), isCompleted = true)
        }

        // Habit 2: Weekly habit on Sundays only, missed the Sunday
        val weeklyHabit = createHabit(
            id = "h_weekly",
            name = "Weekly Review",
            schedule = HabitSchedule.SpecificDays(setOf(java.time.DayOfWeek.SUNDAY))
        )
        val weeklyRecords = emptyList<HabitRecord>()

        val summary = useCase.execute(
            habits = listOf(dailyHabit, weeklyHabit),
            recordsByHabit = mapOf("h_daily" to dailyRecords, "h_weekly" to weeklyRecords),
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        // Total eligible scheduled: 7 (daily) + 1 (weekly Sunday) = 8
        // Total completed: 7 (daily) + 0 (weekly) = 7
        // Rate = 7 / 8 * 100 = 87.5%
        // (A naive unweighted average would falsely claim 50%)
        assertEquals(7, summary.totalCompletedDays)
        assertEquals(8, summary.totalScheduledDays)
        assertEquals(87.5f, summary.globalCompletionRate, 0.001f)
    }

    @Test
    fun testArchivedHabits_areExcludedFromGlobalAnalytics() {
        val today = LocalDate.of(2026, 9, 14)
        val range = AnalyticsRange.ofDaysEndingAt(today, 7)

        val activeHabit = createHabit(id = "h_active", name = "Active Habit", isArchived = false)
        val archivedHabit = createHabit(id = "h_archived", name = "Archived Habit", isArchived = true)

        val records = mapOf(
            "h_active" to listOf(createRecord("h_active", today.minusDays(1), isCompleted = true)),
            "h_archived" to listOf(createRecord("h_archived", today.minusDays(1), isCompleted = true))
        )

        val summary = useCase.execute(
            habits = listOf(activeHabit, archivedHabit),
            recordsByHabit = records,
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(1, summary.activeHabitsCount)
        assertEquals("Active Habit", summary.habitPerformances.first().habitName)
    }

    @Test
    fun testStreakLeaders_sortedByCurrentStreakDescending() {
        val today = LocalDate.of(2026, 9, 14)
        val range = AnalyticsRange.ofDaysEndingAt(today, 14)

        val habit1 = createHabit(id = "h1", name = "Habit 1")
        val habit2 = createHabit(id = "h2", name = "Habit 2")
        val habit3 = createHabit(id = "h3", name = "Habit 3")

        // H1: 5 day streak
        val h1Records = (1..5).map { createRecord("h1", today.minusDays(it.toLong()), isCompleted = true) }
        // H2: 10 day streak
        val h2Records = (1..10).map { createRecord("h2", today.minusDays(it.toLong()), isCompleted = true) }
        // H3: 0 streak
        val h3Records = emptyList<HabitRecord>()

        val summary = useCase.execute(
            habits = listOf(habit1, habit2, habit3),
            recordsByHabit = mapOf("h1" to h1Records, "h2" to h2Records, "h3" to h3Records),
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(2, summary.streakLeaders.size)
        assertEquals("h2", summary.streakLeaders[0].habitId)
        assertEquals(10, summary.streakLeaders[0].currentStreak)
        assertEquals("h1", summary.streakLeaders[1].habitId)
        assertEquals(5, summary.streakLeaders[1].currentStreak)
    }

    @Test
    fun testQuantitativeHabitFormatting_preservesTargetAndUnit() {
        val today = LocalDate.of(2026, 9, 14)
        val range = AnalyticsRange.ofDaysEndingAt(today, 7)

        val countHabit = createHabit(
            id = "h_count",
            name = "Pushups",
            measurement = MeasurementType.Count(50, "reps")
        )
        val durationHabit = createHabit(
            id = "h_duration",
            name = "Study",
            measurement = MeasurementType.Duration(60)
        )
        val quantityHabit = createHabit(
            id = "h_quantity",
            name = "Water",
            measurement = MeasurementType.Quantity(2.5, "L")
        )

        val summary = useCase.execute(
            habits = listOf(countHabit, durationHabit, quantityHabit),
            recordsByHabit = emptyMap(),
            range = range,
            todayDate = today,
            zoneId = zoneId
        )

        val countPerf = summary.habitPerformances.first { it.habitId == "h_count" }
        assertEquals("50 reps", countPerf.targetFormatted)

        val durationPerf = summary.habitPerformances.first { it.habitId == "h_duration" }
        assertEquals("60 mins", durationPerf.targetFormatted)

        val quantityPerf = summary.habitPerformances.first { it.habitId == "h_quantity" }
        assertEquals("2.5 L", quantityPerf.targetFormatted)
    }
}
