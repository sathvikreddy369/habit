package com.habit1.app.ui.habits.list

import com.habit1.app.domain.model.Category

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

    // Phase 5: Search, Filtering, Sorting & Category Management
    data class UpdateSearchQuery(val query: String) : HabitListUiEvent
    data class SelectStatusFilter(val filter: HabitStatusFilter) : HabitListUiEvent
    data class SelectCategoryFilter(val categoryId: String?) : HabitListUiEvent
    data class SelectSortMode(val sortMode: HabitSortMode) : HabitListUiEvent
    data class OpenCategoryEditor(val category: Category?) : HabitListUiEvent
    data object DismissCategoryEditor : HabitListUiEvent
    data class SaveCategory(val id: String?, val name: String, val colorHex: String?, val icon: String?) : HabitListUiEvent
    data class DeleteCategory(val id: String) : HabitListUiEvent
    data class ToggleManageCategoriesDialog(val open: Boolean) : HabitListUiEvent
}
