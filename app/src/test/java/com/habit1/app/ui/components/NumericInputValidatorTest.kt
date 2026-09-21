package com.habit1.app.ui.components

import com.habit1.app.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericInputValidatorTest {

    @Test
    fun count_validIntegers() {
        val countType = MeasurementType.Count(target = 50, unit = "reps")

        val result50 = NumericInputValidator.validate("50", countType)
        assertTrue(result50 is NumericValidationResult.Valid)
        assertEquals(50.0, (result50 as NumericValidationResult.Valid).value, 0.001)

        val result100 = NumericInputValidator.validate("100", countType)
        assertTrue(result100 is NumericValidationResult.Valid)
        assertEquals(100.0, (result100 as NumericValidationResult.Valid).value, 0.001)

        val resultZero = NumericInputValidator.validate("0", countType)
        assertTrue(resultZero is NumericValidationResult.Valid)
        assertEquals(0.0, (resultZero as NumericValidationResult.Valid).value, 0.001)
    }

    @Test
    fun count_rejectsDecimalsAndNegatives() {
        val countType = MeasurementType.Count(target = 50, unit = "reps")

        val resultDecimal = NumericInputValidator.validate("3.5", countType)
        assertTrue(resultDecimal is NumericValidationResult.Invalid)

        val resultNegative = NumericInputValidator.validate("-5", countType)
        assertTrue(resultNegative is NumericValidationResult.Invalid)

        val resultText = NumericInputValidator.validate("abc", countType)
        assertTrue(resultText is NumericValidationResult.Invalid)
    }

    @Test
    fun duration_validMinutes() {
        val durationType = MeasurementType.Duration(targetMinutes = 90)

        val result90 = NumericInputValidator.validate("90", durationType)
        assertTrue(result90 is NumericValidationResult.Valid)
        assertEquals(90.0, (result90 as NumericValidationResult.Valid).value, 0.001)

        val result15 = NumericInputValidator.validate("15", durationType)
        assertTrue(result15 is NumericValidationResult.Valid)
        assertEquals(15.0, (result15 as NumericValidationResult.Valid).value, 0.001)
    }

    @Test
    fun duration_rejectsDecimalsAndNegatives() {
        val durationType = MeasurementType.Duration(targetMinutes = 90)

        val resultDecimal = NumericInputValidator.validate("15.5", durationType)
        assertTrue(resultDecimal is NumericValidationResult.Invalid)

        val resultNegative = NumericInputValidator.validate("-10", durationType)
        assertTrue(resultNegative is NumericValidationResult.Invalid)
    }

    @Test
    fun quantity_validDecimalsAndIntegers() {
        val quantityType = MeasurementType.Quantity(target = 3.25, unit = "L")

        val result325 = NumericInputValidator.validate("3.25", quantityType)
        assertTrue(result325 is NumericValidationResult.Valid)
        assertEquals(3.25, (result325 as NumericValidationResult.Valid).value, 0.0001)

        val resultHalf = NumericInputValidator.validate("0.5", quantityType)
        assertTrue(resultHalf is NumericValidationResult.Valid)
        assertEquals(0.5, (resultHalf as NumericValidationResult.Valid).value, 0.0001)

        val resultInt = NumericInputValidator.validate("5", quantityType)
        assertTrue(resultInt is NumericValidationResult.Valid)
        assertEquals(5.0, (resultInt as NumericValidationResult.Valid).value, 0.0001)
    }

    @Test
    fun quantity_rejectsNegativesAndInvalidText() {
        val quantityType = MeasurementType.Quantity(target = 3.25, unit = "L")

        val resultNegative = NumericInputValidator.validate("-1.5", quantityType)
        assertTrue(resultNegative is NumericValidationResult.Invalid)

        val resultText = NumericInputValidator.validate("invalid", quantityType)
        assertTrue(resultText is NumericValidationResult.Invalid)
    }

    @Test
    fun emptyOrBlankInput_defaultsToZero() {
        val countType = MeasurementType.Count(target = 20, unit = "reps")
        val resultEmpty = NumericInputValidator.validate("", countType)
        assertTrue(resultEmpty is NumericValidationResult.Valid)
        assertEquals(0.0, (resultEmpty as NumericValidationResult.Valid).value, 0.001)

        val resultSpaces = NumericInputValidator.validate("   ", countType)
        assertTrue(resultSpaces is NumericValidationResult.Valid)
        assertEquals(0.0, (resultSpaces as NumericValidationResult.Valid).value, 0.001)
    }
}
