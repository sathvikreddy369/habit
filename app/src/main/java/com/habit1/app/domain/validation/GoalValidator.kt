package com.habit1.app.domain.validation

import java.time.LocalDate

/**
 * Pure, UI-independent typed validation errors for Daily Goals and Subtasks.
 */
sealed interface GoalValidationError {
    data object TitleBlank : GoalValidationError
    data class TitleTooLong(val maxLength: Int) : GoalValidationError
    data class NotesTooLong(val maxLength: Int) : GoalValidationError
    data object TargetDateNull : GoalValidationError
    data object SubtaskTitleBlank : GoalValidationError
    data class SubtaskTitleTooLong(val maxLength: Int) : GoalValidationError
}

/**
 * Pure domain validator for Daily Goals and their Subtasks.
 * Independent of Android UI frameworks and presentation strings.
 */
object GoalValidator {

    const val MAX_TITLE_LENGTH = 200
    const val MAX_NOTES_LENGTH = 1000
    const val MAX_SUBTASK_TITLE_LENGTH = 150

    fun validateGoal(
        title: String,
        notes: String? = null,
        targetDate: LocalDate? = null
    ): List<GoalValidationError> {
        val errors = mutableListOf<GoalValidationError>()

        if (title.isBlank()) {
            errors.add(GoalValidationError.TitleBlank)
        } else if (title.trim().length > MAX_TITLE_LENGTH) {
            errors.add(GoalValidationError.TitleTooLong(MAX_TITLE_LENGTH))
        }

        if (notes != null && notes.length > MAX_NOTES_LENGTH) {
            errors.add(GoalValidationError.NotesTooLong(MAX_NOTES_LENGTH))
        }

        if (targetDate == null) {
            errors.add(GoalValidationError.TargetDateNull)
        }

        return errors
    }

    fun validateSubtask(title: String): List<GoalValidationError> {
        val errors = mutableListOf<GoalValidationError>()

        if (title.isBlank()) {
            errors.add(GoalValidationError.SubtaskTitleBlank)
        } else if (title.trim().length > MAX_SUBTASK_TITLE_LENGTH) {
            errors.add(GoalValidationError.SubtaskTitleTooLong(MAX_SUBTASK_TITLE_LENGTH))
        }

        return errors
    }
}
