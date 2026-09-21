package com.habit1.app.ui.components

import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QuantitativePerformanceCardTest {

    private val baseDate = LocalDate.of(2026, 9, 1)

    @Test
    fun testFormatMeasurementUnit_respectsConfiguration() {
        assertEquals("reps", formatMeasurementUnit(MeasurementType.Count(20, null)))
        assertEquals("pages", formatMeasurementUnit(MeasurementType.Count(20, "pages")))
        assertEquals("min", formatMeasurementUnit(MeasurementType.Duration(30)))
        assertEquals("km", formatMeasurementUnit(MeasurementType.Quantity(5.0, "km")))
        assertEquals("", formatMeasurementUnit(MeasurementType.BooleanChoice))
    }

    @Test
    fun testFormatQuantityValue_omitsTrailingZerosForWholeNumbers() {
        assertEquals("45", formatQuantityValue(45.0))
        assertEquals("0", formatQuantityValue(0.0))
        assertEquals("100", formatQuantityValue(100.0))
        assertEquals("2.5", formatQuantityValue(2.5))
        assertEquals("45.8", formatQuantityValue(45.78))
    }

    @Test
    fun testCalculateDistribution_categorizesCorrectly() {
        val days = listOf(
            // 2 Below target
            HabitHistoryDay(baseDate, CalendarDayStatus.RecordedIncomplete(15.0, 60.0, "min", "DURATION")),
            HabitHistoryDay(baseDate.plusDays(1), CalendarDayStatus.RecordedIncomplete(30.0, 60.0, "min", "DURATION")),
            // 1 At target
            HabitHistoryDay(baseDate.plusDays(2), CalendarDayStatus.Completed(60.0, 60.0, "min", "DURATION")),
            // 2 Above target (preserves overflow)
            HabitHistoryDay(baseDate.plusDays(3), CalendarDayStatus.Completed(75.0, 60.0, "min", "DURATION")),
            HabitHistoryDay(baseDate.plusDays(4), CalendarDayStatus.Completed(90.0, 60.0, "min", "DURATION")),
            // Unrecorded days (must be excluded from distribution!)
            HabitHistoryDay(baseDate.plusDays(5), CalendarDayStatus.ProjectedMissed),
            HabitHistoryDay(baseDate.plusDays(6), CalendarDayStatus.ProjectedRest),
            HabitHistoryDay(baseDate.plusDays(7), CalendarDayStatus.Paused),
            HabitHistoryDay(baseDate.plusDays(8), CalendarDayStatus.PreCreation),
            HabitHistoryDay(baseDate.plusDays(9), CalendarDayStatus.Future)
        )

        val distribution = calculateQuantitativeDistribution(days)

        assertEquals(5, distribution.totalRecordedDays)
        assertEquals(2, distribution.belowTargetCount)
        assertEquals(1, distribution.atTargetCount)
        assertEquals(2, distribution.aboveTargetCount)

        assertEquals(40.0f, distribution.belowTargetPercent, 0.01f)
        assertEquals(20.0f, distribution.atTargetPercent, 0.01f)
        assertEquals(40.0f, distribution.aboveTargetPercent, 0.01f)
    }

    @Test
    fun testZeroVsMissing_recordedZeroIsFactualAttempt() {
        val days = listOf(
            // Explicitly recorded 0 (e.g. attempted but logged 0)
            HabitHistoryDay(baseDate, CalendarDayStatus.RecordedIncomplete(0.0, 50.0, "pages", "COUNT")),
            // Unrecorded missing day
            HabitHistoryDay(baseDate.plusDays(1), CalendarDayStatus.ProjectedMissed)
        )

        val distribution = calculateQuantitativeDistribution(days)

        // Only the recorded 0 counts in distribution (under below target)
        assertEquals(1, distribution.totalRecordedDays)
        assertEquals(1, distribution.belowTargetCount)
        assertEquals(0, distribution.atTargetCount)
        assertEquals(0, distribution.aboveTargetCount)
    }

    @Test
    fun testHistoricalTargetIntegrity_evaluatesAgainstSnapshotTarget() {
        val days = listOf(
            // Historical record when target was 30
            HabitHistoryDay(baseDate, CalendarDayStatus.Completed(30.0, 30.0, "min", "DURATION")),
            // Later record when target was 60
            HabitHistoryDay(baseDate.plusDays(1), CalendarDayStatus.Completed(60.0, 60.0, "min", "DURATION"))
        )

        val distribution = calculateQuantitativeDistribution(days)

        // Both met their snapshot target!
        assertEquals(2, distribution.totalRecordedDays)
        assertEquals(0, distribution.belowTargetCount)
        assertEquals(2, distribution.atTargetCount)
        assertEquals(0, distribution.aboveTargetCount)
    }

    @Test
    fun testEmptyDistribution_safeZeroDivision() {
        val distribution = calculateQuantitativeDistribution(emptyList())

        assertEquals(0, distribution.totalRecordedDays)
        assertEquals(0.0f, distribution.belowTargetPercent, 0.001f)
        assertEquals(0.0f, distribution.atTargetPercent, 0.001f)
        assertEquals(0.0f, distribution.aboveTargetPercent, 0.001f)
    }
}
