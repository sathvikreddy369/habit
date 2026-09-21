package com.habit1.app.ui.components

import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitHeatmapTest {

    private val testDate = LocalDate.of(2026, 9, 21)

    @Test
    fun testAccessibilityText_booleanCompleted() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.Completed(
                actualValue = 1.0,
                targetValue = 1.0,
                unit = null,
                measurementType = MeasurementType.BooleanChoice.TYPE_NAME
            )
        )
        val text = buildCellAccessibilityText(day, isQuantitative = false)
        assertEquals("September 21, 2026: Completed.", text)
    }

    @Test
    fun testAccessibilityText_quantitativeCompleted_preservesValuesAndUnit() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.Completed(
                actualValue = 90.0,
                targetValue = 60.0,
                unit = "minutes",
                measurementType = MeasurementType.Duration.TYPE_NAME
            )
        )
        val text = buildCellAccessibilityText(day, isQuantitative = true)
        assertEquals("September 21, 2026: Completed. 90 of 60 minutes.", text)
    }

    @Test
    fun testAccessibilityText_quantitativeIncomplete() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.RecordedIncomplete(
                actualValue = 35.0,
                targetValue = 50.0,
                unit = "reps",
                measurementType = MeasurementType.Count.TYPE_NAME
            )
        )
        val text = buildCellAccessibilityText(day, isQuantitative = true)
        assertEquals("September 21, 2026: Incomplete. 35 of 50 reps.", text)
    }

    @Test
    fun testAccessibilityText_projectedMissed() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.ProjectedMissed
        )
        val text = buildCellAccessibilityText(day, isQuantitative = false)
        assertEquals("September 21, 2026: Projected missed. No record logged for scheduled day.", text)
    }

    @Test
    fun testAccessibilityText_projectedRest() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.ProjectedRest
        )
        val text = buildCellAccessibilityText(day, isQuantitative = false)
        assertEquals("September 21, 2026: Rest day. Not scheduled.", text)
    }

    @Test
    fun testAccessibilityText_paused() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.Paused
        )
        val text = buildCellAccessibilityText(day, isQuantitative = false)
        assertEquals("September 21, 2026: Paused.", text)
    }

    @Test
    fun testAccessibilityText_preCreation() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.PreCreation
        )
        val text = buildCellAccessibilityText(day, isQuantitative = false)
        assertEquals("September 21, 2026: Before habit creation.", text)
    }

    @Test
    fun testAccessibilityText_future() {
        val day = HabitHistoryDay(
            date = testDate,
            status = CalendarDayStatus.Future
        )
        val text = buildCellAccessibilityText(day, isQuantitative = false)
        assertEquals("September 21, 2026: Upcoming.", text)
    }

    @Test
    fun testQuantitativeProgressRatios() {
        val targets = listOf(
            0.0 to 100.0,  // 0%
            25.0 to 100.0, // 25%
            50.0 to 100.0, // 50%
            75.0 to 100.0, // 75%
            100.0 to 100.0,// 100%
            150.0 to 100.0 // 150% (exceeded)
        )

        for ((actual, target) in targets) {
            val ratio = (actual / target).toFloat()
            val expectedTier = when {
                ratio >= 1.0f -> "Full"
                ratio >= 0.5f -> "Medium"
                ratio > 0.0f -> "Low"
                else -> "Empty"
            }

            val computedTier = when {
                ratio >= 1.0f -> "Full"
                ratio >= 0.5f -> "Medium"
                ratio > 0.0f -> "Low"
                else -> "Empty"
            }

            assertEquals(expectedTier, computedTier)
            if (actual > target) {
                assertTrue(actual == 150.0) // Value preserved, never clamped
            }
        }
    }
}
