package com.habit1.app.ui.today

import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.model.StreakResult
import java.time.LocalDate

/**
 * Immutable UI model representing a habit item on the Today screen.
 */
data class TodayHabitItem(
    val id: String,
    val name: String,
    val description: String?,
    val measurementType: MeasurementType,
    val targetValue: Double,
    val actualValue: Double,
    val unit: String?,
    val isCompleted: Boolean,
    val progressRatio: Float,
    val formattedProgress: String,
    val streakResult: StreakResult
)

/**
 * Immutable UI model representing a subtask on the Today screen.
 */
data class TodaySubtaskItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean
)

/**
 * Immutable UI model representing a daily goal on the Today screen.
 */
data class TodayGoalItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val subtasks: List<TodaySubtaskItem> = emptyList(),
    val notes: String? = null
)

/**
 * Full immutable UI state for the Today screen.
 */
data class TodayUiState(
    val currentDate: LocalDate,
    val formattedDate: String,
    val habits: List<TodayHabitItem> = emptyList(),
    val goals: List<TodayGoalItem> = emptyList(),
    val completedHabitsCount: Int = 0,
    val totalScheduledHabitsCount: Int = 0,
    val completedGoalsCount: Int = 0,
    val totalGoalsCount: Int = 0,
    val overallProgress: Float = 0.0f,
    val isLoading: Boolean = false,
    val userMessage: String? = null
)
