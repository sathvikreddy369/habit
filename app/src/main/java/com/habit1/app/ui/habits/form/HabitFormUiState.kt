package com.habit1.app.ui.habits.form

import com.habit1.app.domain.validation.HabitValidationError
import com.habit1.app.domain.validation.MeasurementKind
import com.habit1.app.domain.validation.ScheduleKind
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Immutable state for creating or editing a habit.
 * Uses typed models for measurement kind and schedule kind per product specifications.
 */
data class HabitFormUiState(
    val habitId: String? = null,
    val name: String = "",
    val description: String = "",
    val measurementKind: MeasurementKind = MeasurementKind.BOOLEAN,
    val targetInput: String = "1",
    val unitInput: String = "",
    val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    val selectedDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val intervalDaysInput: String = "2",
    val anchorDate: LocalDate = LocalDate.now(),
    val reminderTimeInput: String = "",
    val errors: Set<HabitValidationError> = emptySet(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isEditMode: Boolean = false,
    val originalCreatedAt: Long? = null,
    val originalDisplayOrder: Int = 0,
    val originalIsPaused: Boolean = false,
    val originalIsArchived: Boolean = false
) {
    val canSave: Boolean
        get() = name.isNotBlank() && errors.isEmpty() && !isSaving
}
