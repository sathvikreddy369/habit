package com.habit1.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalValidatorTest {

    private val today = LocalDate.of(2026, 9, 21)

    @Test
    fun validateGoal_validGoal_returnsNoErrors() {
        val errors = GoalValidator.validateGoal(
            title = "Ship Phase 6",
            notes = "Finish testing and code reviews.",
            targetDate = today
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateGoal_blankTitle_returnsTitleBlankError() {
        val errors = GoalValidator.validateGoal(
            title = "   ",
            notes = null,
            targetDate = today
        )
        assertEquals(1, errors.size)
        assertTrue(errors.contains(GoalValidationError.TitleBlank))
    }

    @Test
    fun validateGoal_titleTooLong_returnsTitleTooLongError() {
        val longTitle = "A".repeat(201)
        val errors = GoalValidator.validateGoal(
            title = longTitle,
            notes = null,
            targetDate = today
        )
        assertEquals(1, errors.size)
        assertEquals(GoalValidationError.TitleTooLong(200), errors[0])
    }

    @Test
    fun validateGoal_notesTooLong_returnsNotesTooLongError() {
        val longNotes = "N".repeat(1001)
        val errors = GoalValidator.validateGoal(
            title = "Valid Title",
            notes = longNotes,
            targetDate = today
        )
        assertEquals(1, errors.size)
        assertEquals(GoalValidationError.NotesTooLong(1000), errors[0])
    }

    @Test
    fun validateGoal_nullTargetDate_returnsTargetDateNullError() {
        val errors = GoalValidator.validateGoal(
            title = "Valid Title",
            notes = null,
            targetDate = null
        )
        assertEquals(1, errors.size)
        assertTrue(errors.contains(GoalValidationError.TargetDateNull))
    }

    @Test
    fun validateSubtask_validTitle_returnsNoErrors() {
        val errors = GoalValidator.validateSubtask("Write unit tests")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateSubtask_blankTitle_returnsSubtaskTitleBlankError() {
        val errors = GoalValidator.validateSubtask("   ")
        assertEquals(1, errors.size)
        assertTrue(errors.contains(GoalValidationError.SubtaskTitleBlank))
    }

    @Test
    fun validateSubtask_titleTooLong_returnsSubtaskTitleTooLongError() {
        val longTitle = "S".repeat(151)
        val errors = GoalValidator.validateSubtask(longTitle)
        assertEquals(1, errors.size)
        assertEquals(GoalValidationError.SubtaskTitleTooLong(150), errors[0])
    }
}
