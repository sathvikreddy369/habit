package com.habit1.app.domain.usecase

import com.habit1.app.domain.model.MeasurementType

/**
 * Domain rules evaluating completion status and progress for different measurement types.
 *
 * Authoritative Rule (Canonical Semantics):
 * - For BOOLEAN habits: The user's explicit action directly determines completion. When marked done,
 *   isCompleted = true and actualValue = 1.0; when marked undone, isCompleted = false and actualValue = 0.0.
 * - For QUANTITATIVE habits (Count, Duration, Quantity): Completion is derived mathematically from
 *   actualValue >= target.
 * - Single Source of Truth: In all persistent storage, queries, and streak calculations, HabitRecord.isCompleted
 *   is the sole authoritative verdict.
 */
class EvaluateMeasurementUseCase {

    /**
     * Authoritative evaluation:
     * For BooleanChoice: actualValue >= 1.0 (set directly by user toggle).
     * For Quantitative types: actualValue >= target.
     */
    fun isCompleted(measurement: MeasurementType, actualValue: Double): Boolean {
        return when (measurement) {
            is MeasurementType.BooleanChoice -> actualValue >= 1.0
            is MeasurementType.Count -> actualValue >= measurement.target.toDouble()
            is MeasurementType.Duration -> actualValue >= measurement.targetMinutes.toDouble()
            is MeasurementType.Quantity -> actualValue >= measurement.target
        }
    }

    /**
     * Evaluates completion using a historical snapshot stored in a HabitRecord.
     */
    fun isSnapshotCompleted(measurementType: String, targetValue: Double, actualValue: Double): Boolean {
        return when (measurementType) {
            MeasurementType.BooleanChoice.TYPE_NAME -> actualValue >= 1.0
            else -> actualValue >= targetValue && targetValue > 0.0
        }
    }

    /**
     * Returns the completion ratio in range [0.0f, 1.0f] (or >1.0f if exceeded).
     */
    fun progressRatio(measurement: MeasurementType, actualValue: Double): Float {
        val target = when (measurement) {
            is MeasurementType.BooleanChoice -> 1.0
            is MeasurementType.Count -> measurement.target.toDouble()
            is MeasurementType.Duration -> measurement.targetMinutes.toDouble()
            is MeasurementType.Quantity -> measurement.target
        }
        if (target <= 0.0) return if (actualValue > 0.0) 1.0f else 0.0f
        return (actualValue / target).toFloat().coerceAtLeast(0.0f)
    }

    /**
     * Returns a recommended increment step for numerical adjustments.
     */
    fun defaultStep(measurement: MeasurementType): Double {
        return when (measurement) {
            is MeasurementType.BooleanChoice -> 1.0
            is MeasurementType.Count -> 1.0
            is MeasurementType.Duration -> 5.0 // 5-minute increments
            is MeasurementType.Quantity -> 0.25 // e.g. 0.25 Liters or 0.25 km
        }
    }

    /**
     * Returns a clean, human-readable representation of progress.
     */
    fun formatProgress(measurement: MeasurementType, actualValue: Double): String {
        return when (measurement) {
            is MeasurementType.BooleanChoice -> {
                if (actualValue >= 1.0) "Completed" else "Not completed"
            }
            is MeasurementType.Count -> {
                val current = actualValue.toInt()
                val target = measurement.target
                val unitStr = measurement.unit?.let { " $it" } ?: ""
                "$current / $target$unitStr"
            }
            is MeasurementType.Duration -> {
                val current = actualValue.toInt()
                val target = measurement.targetMinutes
                "$current / $target mins"
            }
            is MeasurementType.Quantity -> {
                val current = "%.1f".format(actualValue).removeSuffix(".0")
                val target = "%.1f".format(measurement.target).removeSuffix(".0")
                "$current / $target ${measurement.unit}"
            }
        }
    }
}
