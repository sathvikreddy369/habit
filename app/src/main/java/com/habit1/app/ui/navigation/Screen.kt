package com.habit1.app.ui.navigation

/**
 * Lightweight, deterministic screen destinations for the application.
 */
sealed interface Screen {
    data object Today : Screen
    data object HabitList : Screen
    data class HabitForm(val habitId: String? = null) : Screen
    data object History : Screen
    data class HabitHistory(val habitId: String) : Screen
}
