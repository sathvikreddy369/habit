package com.habit1.app.domain.validation

import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType

/**
 * Strongly-typed domain validation errors for Habit configuration.
 * Pure and independent of the UI layer.
 */
sealed interface HabitValidationError {
    data object NameBlank : HabitValidationError
    data class NameTooLong(val maxLength: Int = 100) : HabitValidationError
    data object TargetMustBePositive : HabitValidationError
    data object UnitRequired : HabitValidationError
    data object SpecificDaysEmpty : HabitValidationError
    data class IntervalTooSmall(val minDays: Int = 2) : HabitValidationError
    data object InvalidConfiguration : HabitValidationError
}

/**
 * Result of validating a habit's definition.
 */
sealed interface HabitValidationResult {
    data object Valid : HabitValidationResult
    data class Invalid(val errors: Set<HabitValidationError>) : HabitValidationResult
}

/**
 * Pure domain validator for Habit fields, measurement configurations, and schedules.
 */
object HabitValidator {

    const val MAX_NAME_LENGTH = 100
    const val MIN_INTERVAL_DAYS = 2

    fun validateName(name: String): HabitValidationError? {
        if (name.isBlank()) {
            return HabitValidationError.NameBlank
        }
        if (name.trim().length > MAX_NAME_LENGTH) {
            return HabitValidationError.NameTooLong(MAX_NAME_LENGTH)
        }
        return null
    }

    fun validateMeasurement(measurement: MeasurementType): Set<HabitValidationError> {
        val errors = mutableSetOf<HabitValidationError>()
        when (measurement) {
            is MeasurementType.BooleanChoice -> {
                // Always valid
            }
            is MeasurementType.Count -> {
                if (measurement.target <= 0) {
                    errors.add(HabitValidationError.TargetMustBePositive)
                }
            }
            is MeasurementType.Duration -> {
                if (measurement.targetMinutes <= 0) {
                    errors.add(HabitValidationError.TargetMustBePositive)
                }
            }
            is MeasurementType.Quantity -> {
                if (measurement.target <= 0.0) {
                    errors.add(HabitValidationError.TargetMustBePositive)
                }
                if (measurement.unit.isBlank()) {
                    errors.add(HabitValidationError.UnitRequired)
                }
            }
        }
        return errors
    }

    fun validateSchedule(schedule: HabitSchedule): Set<HabitValidationError> {
        val errors = mutableSetOf<HabitValidationError>()
        when (schedule) {
            is HabitSchedule.Daily -> {
                // Always valid
            }
            is HabitSchedule.SpecificDays -> {
                if (schedule.days.isEmpty()) {
                    errors.add(HabitValidationError.SpecificDaysEmpty)
                }
            }
            is HabitSchedule.Interval -> {
                if (schedule.everyNDays < MIN_INTERVAL_DAYS) {
                    errors.add(HabitValidationError.IntervalTooSmall(MIN_INTERVAL_DAYS))
                }
            }
        }
        return errors
    }

    fun validateRawMeasurement(
        kind: MeasurementKind,
        targetStr: String,
        unitStr: String
    ): Set<HabitValidationError> {
        val errors = mutableSetOf<HabitValidationError>()
        when (kind) {
            MeasurementKind.BOOLEAN -> {
                // No target or unit needed
            }
            MeasurementKind.COUNT -> {
                val target = targetStr.trim().toIntOrNull()
                if (target == null || target <= 0) {
                    errors.add(HabitValidationError.TargetMustBePositive)
                }
            }
            MeasurementKind.DURATION -> {
                val target = targetStr.trim().toIntOrNull()
                if (target == null || target <= 0) {
                    errors.add(HabitValidationError.TargetMustBePositive)
                }
            }
            MeasurementKind.QUANTITY -> {
                val target = targetStr.trim().toDoubleOrNull()
                if (target == null || target <= 0.0) {
                    errors.add(HabitValidationError.TargetMustBePositive)
                }
                if (unitStr.trim().isBlank()) {
                    errors.add(HabitValidationError.UnitRequired)
                }
            }
        }
        return errors
    }

    fun validateRawSchedule(
        kind: ScheduleKind,
        selectedDays: Set<java.time.DayOfWeek>,
        intervalDaysStr: String
    ): Set<HabitValidationError> {
        val errors = mutableSetOf<HabitValidationError>()
        when (kind) {
            ScheduleKind.DAILY -> {
                // Always valid
            }
            ScheduleKind.SPECIFIC_DAYS -> {
                if (selectedDays.isEmpty()) {
                    errors.add(HabitValidationError.SpecificDaysEmpty)
                }
            }
            ScheduleKind.INTERVAL -> {
                val days = intervalDaysStr.trim().toIntOrNull()
                if (days == null || days < MIN_INTERVAL_DAYS) {
                    errors.add(HabitValidationError.IntervalTooSmall(MIN_INTERVAL_DAYS))
                }
            }
        }
        return errors
    }

    fun validate(
        name: String,
        measurement: MeasurementType,
        schedule: HabitSchedule
    ): HabitValidationResult {
        val errors = mutableSetOf<HabitValidationError>()

        validateName(name)?.let { errors.add(it) }
        errors.addAll(validateMeasurement(measurement))
        errors.addAll(validateSchedule(schedule))

        return if (errors.isEmpty()) {
            HabitValidationResult.Valid
        } else {
            HabitValidationResult.Invalid(errors)
        }
    }
}

enum class MeasurementKind {
    BOOLEAN,
    COUNT,
    DURATION,
    QUANTITY
}

enum class ScheduleKind {
    DAILY,
    SPECIFIC_DAYS,
    INTERVAL
}

