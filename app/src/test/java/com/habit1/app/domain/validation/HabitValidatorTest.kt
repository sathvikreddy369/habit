package com.habit1.app.domain.validation

import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitValidatorTest {

    @Test
    fun validateName_blankReturnsNameBlank() {
        assertEquals(HabitValidationError.NameBlank, HabitValidator.validateName(""))
        assertEquals(HabitValidationError.NameBlank, HabitValidator.validateName("   "))
    }

    @Test
    fun validateName_tooLongReturnsNameTooLong() {
        val longName = "A".repeat(101)
        val error = HabitValidator.validateName(longName)
        assertTrue(error is HabitValidationError.NameTooLong)
    }

    @Test
    fun validateName_validNameReturnsNull() {
        assertNull(HabitValidator.validateName("Morning Meditation"))
        assertNull(HabitValidator.validateName("A".repeat(100)))
    }

    @Test
    fun validateMeasurement_allValidMeasurementTypes() {
        assertTrue(HabitValidator.validateMeasurement(MeasurementType.BooleanChoice).isEmpty())
        assertTrue(HabitValidator.validateMeasurement(MeasurementType.Count(10, "reps")).isEmpty())
        assertTrue(HabitValidator.validateMeasurement(MeasurementType.Duration(30)).isEmpty())
        assertTrue(HabitValidator.validateMeasurement(MeasurementType.Quantity(2.5, "L")).isEmpty())
    }

    @Test
    fun validateRawMeasurement_validatesNonPositiveAndNegativeTargets() {
        // Count non-positive / negative / invalid string
        val zeroCount = HabitValidator.validateRawMeasurement(MeasurementKind.COUNT, "0", "")
        assertTrue(zeroCount.contains(HabitValidationError.TargetMustBePositive))

        val negativeCount = HabitValidator.validateRawMeasurement(MeasurementKind.COUNT, "-5", "")
        assertTrue(negativeCount.contains(HabitValidationError.TargetMustBePositive))

        val nonNumberCount = HabitValidator.validateRawMeasurement(MeasurementKind.COUNT, "abc", "")
        assertTrue(nonNumberCount.contains(HabitValidationError.TargetMustBePositive))

        // Duration non-positive / negative
        val zeroDuration = HabitValidator.validateRawMeasurement(MeasurementKind.DURATION, "0", "")
        assertTrue(zeroDuration.contains(HabitValidationError.TargetMustBePositive))

        val negDuration = HabitValidator.validateRawMeasurement(MeasurementKind.DURATION, "-10", "")
        assertTrue(negDuration.contains(HabitValidationError.TargetMustBePositive))

        // Quantity non-positive / negative
        val zeroQty = HabitValidator.validateRawMeasurement(MeasurementKind.QUANTITY, "0.0", "L")
        assertTrue(zeroQty.contains(HabitValidationError.TargetMustBePositive))

        val negQty = HabitValidator.validateRawMeasurement(MeasurementKind.QUANTITY, "-1.5", "L")
        assertTrue(negQty.contains(HabitValidationError.TargetMustBePositive))
    }

    @Test
    fun validateRawMeasurement_requiresUnitForQuantity() {
        val blankUnit = HabitValidator.validateRawMeasurement(MeasurementKind.QUANTITY, "2.5", "   ")
        assertTrue(blankUnit.contains(HabitValidationError.UnitRequired))

        val validQty = HabitValidator.validateRawMeasurement(MeasurementKind.QUANTITY, "2.5", "km")
        assertTrue(validQty.isEmpty())
    }

    @Test
    fun validateSchedule_allValidSchedules() {
        assertTrue(HabitValidator.validateSchedule(HabitSchedule.Daily).isEmpty())
        assertTrue(HabitValidator.validateSchedule(HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY))).isEmpty())
        assertTrue(HabitValidator.validateSchedule(HabitSchedule.Interval(2, LocalDate.now())).isEmpty())
    }

    @Test
    fun validateSchedule_emptySpecificDays() {
        val errors = HabitValidator.validateRawSchedule(ScheduleKind.SPECIFIC_DAYS, emptySet(), "2")
        assertTrue(errors.contains(HabitValidationError.SpecificDaysEmpty))
    }

    @Test
    fun validateSchedule_intervalTooSmallOrInvalid() {
        val oneDay = HabitValidator.validateRawSchedule(ScheduleKind.INTERVAL, emptySet(), "1")
        assertTrue(oneDay.any { it is HabitValidationError.IntervalTooSmall })

        val zeroDays = HabitValidator.validateRawSchedule(ScheduleKind.INTERVAL, emptySet(), "0")
        assertTrue(zeroDays.any { it is HabitValidationError.IntervalTooSmall })

        val negDays = HabitValidator.validateRawSchedule(ScheduleKind.INTERVAL, emptySet(), "-2")
        assertTrue(negDays.any { it is HabitValidationError.IntervalTooSmall })

        val nonNumber = HabitValidator.validateRawSchedule(ScheduleKind.INTERVAL, emptySet(), "xyz")
        assertTrue(nonNumber.any { it is HabitValidationError.IntervalTooSmall })

        val validInterval = HabitValidator.validateRawSchedule(ScheduleKind.INTERVAL, emptySet(), "3")
        assertTrue(validInterval.isEmpty())
    }

    @Test
    fun validate_overallValidationPassesAndFailsCorrectly() {
        val validResult = HabitValidator.validate(
            name = "Read Books",
            measurement = MeasurementType.Count(20, "pages"),
            schedule = HabitSchedule.Daily
        )
        assertTrue(validResult is HabitValidationResult.Valid)

        val invalidResult = HabitValidator.validate(
            name = "   ",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily
        )
        assertTrue(invalidResult is HabitValidationResult.Invalid)
        assertEquals(
            setOf(HabitValidationError.NameBlank),
            (invalidResult as HabitValidationResult.Invalid).errors
        )
    }
}
