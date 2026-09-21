package com.habit1.app.ui.components

import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.model.HabitSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CompletionTrendGraphTest {

    private val baseDate = LocalDate.of(2026, 9, 1)

    private fun createSummary(
        measurement: MeasurementType = MeasurementType.BooleanChoice,
        dailyBreakdown: List<HabitHistoryDay>
    ): HabitAnalyticsSummary {
        val habit = Habit(
            id = "habit-trend-1",
            name = "Test Trend Habit",
            measurement = measurement,
            schedule = HabitSchedule.Daily,
            createdAt = Instant.ofEpochMilli(1000L),
            updatedAt = Instant.ofEpochMilli(1000L)
        )
        val range = AnalyticsRange(
            startDate = dailyBreakdown.first().date,
            endDate = dailyBreakdown.last().date
        )
        return HabitAnalyticsSummary(
            habit = habit,
            range = range,
            scheduledDays = dailyBreakdown.count {
                it.status is CalendarDayStatus.Completed ||
                    it.status is CalendarDayStatus.RecordedIncomplete ||
                    it.status is CalendarDayStatus.ProjectedMissed
            },
            completedDays = dailyBreakdown.count { it.status is CalendarDayStatus.Completed },
            recordedIncompleteDays = dailyBreakdown.count { it.status is CalendarDayStatus.RecordedIncomplete },
            missedDays = dailyBreakdown.count { it.status is CalendarDayStatus.ProjectedMissed },
            restDays = dailyBreakdown.count { it.status is CalendarDayStatus.ProjectedRest },
            completionRate = 75.0f,
            currentStreak = 2,
            longestStreak = 5,
            quantitativeStats = null,
            dailyBreakdown = dailyBreakdown
        )
    }

    @Test
    fun testExtractTrendPoints_BooleanHabit_MapsCompletedAndIncomplete() {
        val days = listOf(
            HabitHistoryDay(baseDate, CalendarDayStatus.Completed(1.0, 1.0, null, "BOOLEAN")),
            HabitHistoryDay(baseDate.plusDays(1), CalendarDayStatus.RecordedIncomplete(0.0, 1.0, null, "BOOLEAN")),
            HabitHistoryDay(baseDate.plusDays(2), CalendarDayStatus.ProjectedMissed),
            HabitHistoryDay(baseDate.plusDays(3), CalendarDayStatus.ProjectedRest),
            HabitHistoryDay(baseDate.plusDays(4), CalendarDayStatus.Paused),
            HabitHistoryDay(baseDate.plusDays(5), CalendarDayStatus.PreCreation),
            HabitHistoryDay(baseDate.plusDays(6), CalendarDayStatus.Future)
        )

        val summary = createSummary(MeasurementType.BooleanChoice, days)
        val points = extractTrendPoints(summary)

        // Only Completed, RecordedIncomplete, and ProjectedMissed should produce trend points
        assertEquals(3, points.size)

        // Day 1: Completed
        assertEquals(baseDate, points[0].date)
        assertEquals(100f, points[0].percentage, 0.001f)
        assertTrue(points[0].isFactual)
        assertTrue(points[0].isTargetAchieved)
        assertTrue(points[0].accessibilityText.contains("completed — 100%"))

        // Day 2: Recorded Incomplete
        assertEquals(baseDate.plusDays(1), points[1].date)
        assertEquals(0f, points[1].percentage, 0.001f)
        assertTrue(points[1].isFactual)
        assertFalse(points[1].isTargetAchieved)
        assertTrue(points[1].accessibilityText.contains("incomplete — 0%"))

        // Day 3: Projected Missed
        assertEquals(baseDate.plusDays(2), points[2].date)
        assertEquals(0f, points[2].percentage, 0.001f)
        assertFalse(points[2].isFactual)
        assertFalse(points[2].isTargetAchieved)
        assertTrue(points[2].accessibilityText.contains("projected missed"))
    }

    @Test
    fun testExtractTrendPoints_QuantitativeHabit_CalculatesProgressAccurately() {
        val target = 60.0
        val days = listOf(
            HabitHistoryDay(baseDate, CalendarDayStatus.RecordedIncomplete(0.0, target, "min", "DURATION")),
            HabitHistoryDay(baseDate.plusDays(1), CalendarDayStatus.RecordedIncomplete(15.0, target, "min", "DURATION")),
            HabitHistoryDay(baseDate.plusDays(2), CalendarDayStatus.RecordedIncomplete(30.0, target, "min", "DURATION")),
            HabitHistoryDay(baseDate.plusDays(3), CalendarDayStatus.RecordedIncomplete(45.0, target, "min", "DURATION")),
            HabitHistoryDay(baseDate.plusDays(4), CalendarDayStatus.Completed(60.0, target, "min", "DURATION")),
            HabitHistoryDay(baseDate.plusDays(5), CalendarDayStatus.Completed(90.0, target, "min", "DURATION"))
        )

        val summary = createSummary(MeasurementType.Duration(target.toInt()), days)
        val points = extractTrendPoints(summary)

        assertEquals(6, points.size)

        // 0%
        assertEquals(0f, points[0].percentage, 0.001f)
        assertFalse(points[0].isTargetAchieved)

        // 25% (15/60)
        assertEquals(25f, points[1].percentage, 0.001f)
        assertFalse(points[1].isTargetAchieved)
        assertTrue(points[1].accessibilityText.contains("partial — 15 of 60 min (25%)"))

        // 50% (30/60)
        assertEquals(50f, points[2].percentage, 0.001f)
        assertFalse(points[2].isTargetAchieved)

        // 75% (45/60)
        assertEquals(75f, points[3].percentage, 0.001f)
        assertFalse(points[3].isTargetAchieved)

        // 100% (60/60)
        assertEquals(100f, points[4].percentage, 0.001f)
        assertTrue(points[4].isTargetAchieved)
        assertTrue(points[4].accessibilityText.contains("completed — 60 of 60 min (100%)"))

        // 150% (90/60) - preserves progress > 100%
        assertEquals(150f, points[5].percentage, 0.001f)
        assertTrue(points[5].isTargetAchieved)
        assertTrue(points[5].accessibilityText.contains("completed — 90 of 60 min (150%)"))
    }

    @Test
    fun testExtractTrendPoints_ScheduleGaps_ExcludesRestDays() {
        val days = listOf(
            HabitHistoryDay(baseDate, CalendarDayStatus.Completed(1.0, 1.0, null, "BOOLEAN")), // Mon
            HabitHistoryDay(baseDate.plusDays(1), CalendarDayStatus.ProjectedRest),             // Tue (Rest)
            HabitHistoryDay(baseDate.plusDays(2), CalendarDayStatus.Completed(1.0, 1.0, null, "BOOLEAN")) // Wed
        )

        val summary = createSummary(MeasurementType.BooleanChoice, days)
        val points = extractTrendPoints(summary)

        // Rest day is excluded: only 2 points exist
        assertEquals(2, points.size)
        assertEquals(baseDate, points[0].date)
        assertEquals(baseDate.plusDays(2), points[1].date)
        // Day index in the range is preserved
        assertEquals(0, points[0].dayIndex)
        assertEquals(2, points[1].dayIndex)
    }

    @Test
    fun testExtractTrendPoints_PausedAndPreCreation_NeverTreatedAsMissed() {
        val days = listOf(
            HabitHistoryDay(baseDate, CalendarDayStatus.PreCreation),
            HabitHistoryDay(baseDate.plusDays(1), CalendarDayStatus.Paused),
            HabitHistoryDay(baseDate.plusDays(2), CalendarDayStatus.Completed(1.0, 1.0, null, "BOOLEAN")),
            HabitHistoryDay(baseDate.plusDays(3), CalendarDayStatus.Future)
        )

        val summary = createSummary(MeasurementType.BooleanChoice, days)
        val points = extractTrendPoints(summary)

        assertEquals(1, points.size)
        assertEquals(baseDate.plusDays(2), points[0].date)
        assertTrue(points[0].isTargetAchieved)
    }

    @Test
    fun testExtractTrendPoints_RangeBoundaries() {
        val days = (0 until 30).map { i ->
            HabitHistoryDay(
                date = baseDate.plusDays(i.toLong()),
                status = if (i % 2 == 0) {
                    CalendarDayStatus.Completed(1.0, 1.0, null, "BOOLEAN")
                } else {
                    CalendarDayStatus.ProjectedMissed
                }
            )
        }

        val summary = createSummary(MeasurementType.BooleanChoice, days)
        val points = extractTrendPoints(summary)

        assertEquals(30, points.size)
        assertEquals(baseDate, points.first().date)
        assertEquals(baseDate.plusDays(29), points.last().date)
        assertEquals(0, points.first().dayIndex)
        assertEquals(29, points.last().dayIndex)
        assertEquals(30, points.first().totalRangeDays)
    }
}
