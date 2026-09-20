package com.habit1.app.ui.habits.list

/**
 * Unidirectional events emitted from the Habit Management screen.
 */
sealed interface HabitListUiEvent {
    data class ToggleShowArchived(val showArchived: Boolean) : HabitListUiEvent
    data class PauseHabit(val habitId: String) : HabitListUiEvent
    data class ResumeHabit(val habitId: String) : HabitListUiEvent
    data class ArchiveHabit(val habitId: String) : HabitListUiEvent
    data class UnarchiveHabit(val habitId: String) : HabitListUiEvent
    data class MoveUp(val habitId: String) : HabitListUiEvent
    data class MoveDown(val habitId: String) : HabitListUiEvent
    data class RequestDeleteHabit(val habit: HabitListItem) : HabitListUiEvent
    data object ConfirmDeleteHabit : HabitListUiEvent
    data object CancelDeleteHabit : HabitListUiEvent
}
