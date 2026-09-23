package com.habit1.app.ui.habits.list

/**
 * Immutable UI model representing a habit in the management list.
 */
data class HabitListItem(
    val id: String,
    val name: String,
    val description: String?,
    val measurementSummary: String,
    val scheduleSummary: String,
    val isPaused: Boolean,
    val isArchived: Boolean,
    val displayOrder: Int,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val reminderSummary: String? = null,
    val colorHex: String? = null
)

/**
 * State for the Habit List & Management screen.
 */
data class HabitListUiState(
    val activeHabits: List<HabitListItem> = emptyList(),
    val archivedHabits: List<HabitListItem> = emptyList(),
    val showingArchived: Boolean = false,
    val habitPendingDeletion: HabitListItem? = null,
    val isLoading: Boolean = false
)
