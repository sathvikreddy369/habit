package com.habit1.app.ui.habits.list

import com.habit1.app.domain.model.Category

/**
 * Filter mode for habit management list.
 */
enum class HabitStatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    ARCHIVED("Archived")
}

/**
 * Presentation sorting presets for habit management list (Decision 6.2).
 * Presentation-only: selecting a sort mode does NOT alter persisted display_order.
 */
enum class HabitSortMode(val label: String) {
    MANUAL("Custom Order"),
    NAME_ASC("Name (A–Z)"),
    STREAK_DESC("Current Streak (High to Low)"),
    ADHERENCE_ASC("Adherence (Lowest first)"),
    SCHEDULE_FREQ("Schedule Frequency")
}

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
    val colorHex: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryColorHex: String? = null,
    val categoryIcon: String? = null,
    val currentStreak: Int = 0,
    val adherenceRate: Double = 0.0,
    val scheduleDaysCount: Int = 7
)

/**
 * State for the Habit List & Management screen.
 */
data class HabitListUiState(
    val activeHabits: List<HabitListItem> = emptyList(),
    val archivedHabits: List<HabitListItem> = emptyList(),
    val filteredHabits: List<HabitListItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: HabitStatusFilter = HabitStatusFilter.ALL,
    val selectedCategoryId: String? = null,
    val sortMode: HabitSortMode = HabitSortMode.MANUAL,
    val showingArchived: Boolean = false,
    val habitPendingDeletion: HabitListItem? = null,
    val editingCategory: Category? = null,
    val isCategoryEditorOpen: Boolean = false,
    val isManageCategoriesDialogOpen: Boolean = false,
    val isLoading: Boolean = false
)
