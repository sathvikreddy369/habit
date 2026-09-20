package com.habit1.app.domain

import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.EvaluateMeasurementUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateMeasurementUseCaseTest {

    private val useCase = EvaluateMeasurementUseCase()

    @Test
    fun testBooleanChoiceCompletion() {
        val measurement = MeasurementType.BooleanChoice
        assertFalse(useCase.isCompleted(measurement, 0.0))
        assertFalse(useCase.isCompleted(measurement, 0.5))
        assertTrue(useCase.isCompleted(measurement, 1.0))
        assertTrue(useCase.isCompleted(measurement, 2.0))
    }

    @Test
    fun testCountCompletion() {
        val measurement = MeasurementType.Count(target = 50, unit = "reps")
        assertFalse(useCase.isCompleted(measurement, 0.0))
        assertFalse(useCase.isCompleted(measurement, 49.0))
        assertTrue(useCase.isCompleted(measurement, 50.0))
        assertTrue(useCase.isCompleted(measurement, 60.0))
    }

    @Test
    fun testDurationCompletion() {
        val measurement = MeasurementType.Duration(targetMinutes = 45)
        assertFalse(useCase.isCompleted(measurement, 30.0))
        assertFalse(useCase.isCompleted(measurement, 44.0))
        assertTrue(useCase.isCompleted(measurement, 45.0))
        assertTrue(useCase.isCompleted(measurement, 90.0))
    }

    @Test
    fun testQuantityCompletion() {
        val measurement = MeasurementType.Quantity(target = 2.5, unit = "L")
        assertFalse(useCase.isCompleted(measurement, 1.0))
        assertFalse(useCase.isCompleted(measurement, 2.49))
        assertTrue(useCase.isCompleted(measurement, 2.5))
        assertTrue(useCase.isCompleted(measurement, 3.0))
    }

    @Test
    fun testProgressRatio() {
        val count = MeasurementType.Count(target = 100)
        assertEquals(0.0f, useCase.progressRatio(count, 0.0), 0.001f)
        assertEquals(0.5f, useCase.progressRatio(count, 50.0), 0.001f)
        assertEquals(1.0f, useCase.progressRatio(count, 100.0), 0.001f)
        assertEquals(1.2f, useCase.progressRatio(count, 120.0), 0.001f)
    }

    @Test
    fun testFormatProgress() {
        assertEquals("Completed", useCase.formatProgress(MeasurementType.BooleanChoice, 1.0))
        assertEquals("Not completed", useCase.formatProgress(MeasurementType.BooleanChoice, 0.0))
        assertEquals("25 / 50 reps", useCase.formatProgress(MeasurementType.Count(50, "reps"), 25.0))
        assertEquals("30 / 60 mins", useCase.formatProgress(MeasurementType.Duration(60), 30.0))
        assertEquals("1.5 / 2.5 L", useCase.formatProgress(MeasurementType.Quantity(2.5, "L"), 1.5))
    }

    @Test
    fun testSnapshotEvaluation() {
        // Boolean snapshot
        assertTrue(useCase.isSnapshotCompleted("BOOLEAN", 1.0, 1.0))
        assertFalse(useCase.isSnapshotCompleted("BOOLEAN", 1.0, 0.0))

        // Quantitative snapshot
        assertTrue(useCase.isSnapshotCompleted("QUANTITY", 2.5, 2.5))
        assertTrue(useCase.isSnapshotCompleted("QUANTITY", 2.5, 3.0))
        assertFalse(useCase.isSnapshotCompleted("QUANTITY", 2.5, 2.0))
    }
}
