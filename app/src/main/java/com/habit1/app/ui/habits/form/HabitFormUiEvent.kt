package com.habit1.app.ui.habits.form

import com.habit1.app.domain.validation.MeasurementKind
import com.habit1.app.domain.validation.ScheduleKind
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Unidirectional UI events emitted from the habit creation/edit screen.
 */
sealed interface HabitFormUiEvent {
    data class UpdateName(val name: String) : HabitFormUiEvent
    data class UpdateDescription(val description: String) : HabitFormUiEvent
    data class SelectMeasurementKind(val kind: MeasurementKind) : HabitFormUiEvent
    data class UpdateTarget(val target: String) : HabitFormUiEvent
    data class UpdateUnit(val unit: String) : HabitFormUiEvent
    data class SelectScheduleKind(val kind: ScheduleKind) : HabitFormUiEvent
    data class ToggleDay(val day: DayOfWeek) : HabitFormUiEvent
    data class UpdateIntervalDays(val days: String) : HabitFormUiEvent
    data class UpdateAnchorDate(val date: LocalDate) : HabitFormUiEvent
    data class UpdateReminderTime(val reminderTime: String) : HabitFormUiEvent
    data class UpdateStartDate(val date: LocalDate) : HabitFormUiEvent
    data class UpdateColor(val colorHex: String) : HabitFormUiEvent
    data class SelectCategory(val categoryId: String?) : HabitFormUiEvent
    data class CreateCategory(val name: String, val colorHex: String?, val icon: String?) : HabitFormUiEvent
    data object SaveHabit : HabitFormUiEvent
    data object ResetSaveState : HabitFormUiEvent
    data object ArchiveHabit : HabitFormUiEvent
    data object UnarchiveHabit : HabitFormUiEvent
    data object TogglePauseHabit : HabitFormUiEvent
    data object DeleteHabit : HabitFormUiEvent
}
