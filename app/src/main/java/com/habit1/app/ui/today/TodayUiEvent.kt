package com.habit1.app.ui.today

import com.habit1.app.domain.model.MeasurementType

/**
 * Unidirectional UI events emitted from the Today screen.
 */
sealed interface TodayUiEvent {
    data class ToggleHabit(val habitId: String) : TodayUiEvent
    data class IncrementHabit(val habitId: String) : TodayUiEvent
    data class DecrementHabit(val habitId: String) : TodayUiEvent
    data class SetHabitValue(val habitId: String, val value: Double) : TodayUiEvent
    data class ToggleGoal(val goalId: String) : TodayUiEvent
    data class ToggleSubtask(val goalId: String, val subtaskId: String) : TodayUiEvent
    data class AddGoal(val title: String) : TodayUiEvent
    data class AddHabitQuick(val name: String, val measurementType: MeasurementType) : TodayUiEvent
    data object RefreshDate : TodayUiEvent
    data object DismissMessage : TodayUiEvent
}
