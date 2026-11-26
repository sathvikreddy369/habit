package com.habit1.app.domain

import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitInsight
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.ComputeGlobalAnalyticsUseCase
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import com.habit1.app.domain.usecase.GenerateAnalyticsInsightsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AnalyticsDashboardRedesignTest {

    private val computeHabitAnalytics = ComputeHabitAnalyticsUseCase()
    private val computeGlobalAnalytics = ComputeGlobalAnalyticsUseCase(computeHabitAnalytics)
    private val generateInsights = GenerateAnalyticsInsightsUseCase()

    private val baseDate = LocalDate.of(2026, 11, 26)
    private val testInstant = Instant.parse("2026-11-26T10:00:00Z")

    private val booleanHabit = Habit(
        id = "h_bool",
        name = "Morning Meditation",
        colorHex = "#42A5F5",
        schedule = HabitSchedule.Daily,
        measurement = MeasurementType.BooleanChoice,
        createdAt = testInstant.minusSeconds(86400L * 60),
        updatedAt = testInstant.minusSeconds(86400L * 60),
        startDate = baseDate.minusDays(60)
    )

    private val quantitativeHabit = Habit(
        id = "h_quant",
        name = "Daily Pushups",
        colorHex = "#66BB6A",
        schedule = HabitSchedule.Daily,
        measurement = MeasurementType.Count(target = 30, unit = "reps"),
        createdAt = testInstant.minusSeconds(86400L * 60),
        updatedAt = testInstant.minusSeconds(86400L * 60),
        startDate = baseDate.minusDays(60)
    )

    @Test
    fun computeHabitAnalytics_calculatesPeriodComparison_whenSufficientDataExists() {
        val range = AnalyticsRange.ofDaysEndingAt(baseDate, 14) // 14 days
        val records = mutableListOf<HabitRecord>()

        // Previous 14 days (baseDate - 27 to baseDate - 14): 7 completed
        for (i in 14..20) {
            records.add(
                HabitRecord(
                    id = "r_prev_$i",
                    habitId = booleanHabit.id,
                    date = baseDate.minusDays(i.toLong()),
                    actualValue = 1.0,
                    targetValue = 1.0,
                    measurementType = "BOOLEAN",
                    status = HabitRecordStatus.DONE,
                    recordedAt = testInstant
                )
            )
        }

        // Current 14 days (baseDate - 13 to baseDate): 14 completed
        for (i in 0..13) {
            records.add(
                HabitRecord(
                    id = "r_curr_$i",
                    habitId = booleanHabit.id,
                    date = baseDate.minusDays(i.toLong()),
                    actualValue = 1.0,
                    targetValue = 1.0,
                    measurementType = "BOOLEAN",
                    status = HabitRecordStatus.DONE,
                    recordedAt = testInstant
                )
            )
        }

        val summary = computeHabitAnalytics.execute(
            habit = booleanHabit,
            records = records,
            range = range,
            todayDate = baseDate
        )

        assertNotNull(summary.periodComparison)
        val comp = summary.periodComparison!!
        assertTrue(comp.isSufficientData)
        assertEquals(14, comp.currentCompletedCount)
        assertEquals(7, comp.previousCompletedCount)
        assertEquals(100f, comp.currentCompletionRate, 0.5f)
        assertEquals(50f, comp.previousCompletionRate, 0.5f)
        assertEquals(50f, comp.rateDelta, 0.5f)
        assertEquals(7, comp.completedDelta)

        // Verify generated insights includes improvement
        assertTrue(summary.insights.any { it.type == HabitInsight.InsightType.CONSISTENCY_IMPROVEMENT })
    }

    @Test
    fun computeGlobalAnalytics_aggregatesDailyTrendAndRecordBreakdown() {
        val range = AnalyticsRange.ofDaysEndingAt(baseDate, 7)
        val habits = listOf(booleanHabit, quantitativeHabit)

        val records = listOf(
            HabitRecord(
                id = "r1",
                habitId = booleanHabit.id,
                date = baseDate,
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                status = HabitRecordStatus.DONE,
                recordedAt = testInstant
            ),
            HabitRecord(
                id = "r2",
                habitId = quantitativeHabit.id,
                date = baseDate,
                actualValue = 15.0,
                targetValue = 30.0,
                measurementType = "COUNT",
                unit = "reps",
                status = HabitRecordStatus.PARTIAL,
                recordedAt = testInstant
            ),
            HabitRecord(
                id = "r3",
                habitId = booleanHabit.id,
                date = baseDate.minusDays(1),
                actualValue = 0.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                status = HabitRecordStatus.SKIPPED,
                recordedAt = testInstant
            )
        )

        val summary = computeGlobalAnalytics.execute(
            habits = habits,
            recordsByHabit = records.groupBy { it.habitId },
            range = range,
            todayDate = baseDate
        )

        // Check daily trend points
        assertEquals(7, summary.dailyTrend.size)
        val todayPoint = summary.dailyTrend.first { it.date == baseDate }
        assertEquals(2, todayPoint.scheduledHabitsCount)
        assertEquals(1, todayPoint.completedHabitsCount) // booleanHabit is DONE
        assertEquals(50f, todayPoint.completionRate, 0.5f)

        // Check RecordBreakdown
        assertTrue(summary.recordBreakdown.completedCount >= 1)
        assertTrue(summary.recordBreakdown.partialCount >= 1)
        assertTrue(summary.recordBreakdown.skippedCount >= 1)

        // Check weekday adherence
        assertEquals(7, summary.weekdayAdherence.size)
    }

    @Test
    fun generateAnalyticsInsights_producesDefensibleMilestoneAndWeekdayInsights() {
        // High streak milestone insight
        val range = AnalyticsRange.ofDaysEndingAt(baseDate, 30)
        val records = (0..29).map { i ->
            HabitRecord(
                id = "streak_$i",
                habitId = booleanHabit.id,
                date = baseDate.minusDays(i.toLong()),
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                status = HabitRecordStatus.DONE,
                recordedAt = testInstant
            )
        }

        val summary = computeHabitAnalytics.execute(
            habit = booleanHabit,
            records = records,
            range = range,
            todayDate = baseDate
        )

        assertTrue(summary.currentStreak >= 30)
        val insights = generateInsights.generateHabitInsights(summary)
        assertTrue(insights.any { it.type == HabitInsight.InsightType.STREAK_MILESTONE })
    }
}
