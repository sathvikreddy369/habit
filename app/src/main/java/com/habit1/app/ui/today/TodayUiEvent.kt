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

    // Date Stepper Actions (Decision 2.1)
    data object StepDateBackward : TodayUiEvent
    data object StepDateForward : TodayUiEvent
    data object ReturnToToday : TodayUiEvent
    data class SelectDate(val date: java.time.LocalDate) : TodayUiEvent

    // 5-State Habit Actions & Sheet (Decisions 1.1, 1.3, 2.2)
    data class OpenHabitActionSheet(val habit: TodayHabitItem) : TodayUiEvent
    data object DismissHabitActionSheet : TodayUiEvent
    data class CompleteToTarget(val habitId: String) : TodayUiEvent
    data class MarkSkipped(val habitId: String, val notes: String? = null) : TodayUiEvent
    data class MarkMissed(val habitId: String, val notes: String? = null) : TodayUiEvent
    data class ClearRecord(val habitId: String) : TodayUiEvent
    data object UndoClearRecord : TodayUiEvent
    data class OpenNumericDialog(val habit: TodayHabitItem) : TodayUiEvent
    data object DismissNumericDialog : TodayUiEvent
    data class OpenNotesDialog(val habit: TodayHabitItem) : TodayUiEvent
    data object DismissNotesDialog : TodayUiEvent
    data class SaveNotes(val habitId: String, val notes: String?) : TodayUiEvent

    // Goal Management Dialogs & Actions
    data object OpenAddGoalDialog : TodayUiEvent
    data object DismissGoalDialog : TodayUiEvent
    data class SaveNewGoal(val title: String, val notes: String?, val targetDate: java.time.LocalDate? = null, val reminderTime: java.time.LocalTime? = null) : TodayUiEvent
    data class RequestEditGoal(val goal: TodayGoalItem) : TodayUiEvent
    data class SaveEditedGoal(val goalId: String, val title: String, val notes: String?, val reminderTime: java.time.LocalTime? = null) : TodayUiEvent
    data class SetGoalReminder(val goalId: String, val reminderTime: java.time.LocalTime?) : TodayUiEvent
    data class RequestDeleteGoal(val goal: TodayGoalItem) : TodayUiEvent
    data object ConfirmDeleteGoal : TodayUiEvent
    data object CancelDeleteGoal : TodayUiEvent
    data class RequestMoveGoalTomorrow(val goal: TodayGoalItem) : TodayUiEvent
    data object ConfirmMoveGoalTomorrow : TodayUiEvent
    data object CancelMoveGoalTomorrow : TodayUiEvent
    data object UndoLastMovedGoal : TodayUiEvent
    data class MoveGoalDate(val goalId: String, val newDate: java.time.LocalDate) : TodayUiEvent
    data class MoveGoalUp(val goalId: String) : TodayUiEvent
    data class MoveGoalDown(val goalId: String) : TodayUiEvent
    data class ToggleGoalExpanded(val goalId: String) : TodayUiEvent

    // Subtask Actions
    data class AddSubtask(val goalId: String, val title: String) : TodayUiEvent
    data class RequestEditSubtask(val goalId: String, val subtask: TodaySubtaskItem) : TodayUiEvent
    data class SaveEditedSubtask(val goalId: String, val subtaskId: String, val title: String) : TodayUiEvent
    data object DismissSubtaskEditDialog : TodayUiEvent
    data class DeleteSubtask(val goalId: String, val subtaskId: String) : TodayUiEvent
    data class MoveSubtaskUp(val goalId: String, val subtaskId: String) : TodayUiEvent
    data class MoveSubtaskDown(val goalId: String, val subtaskId: String) : TodayUiEvent

    // Daily Reflection Actions
    data object OpenReviewDialog : TodayUiEvent
    data object DismissReviewDialog : TodayUiEvent
    data class SaveReview(val notes: String?, val mood: String?) : TodayUiEvent
    data object DeleteReview : TodayUiEvent

    // Phase 5 Category Actions (Decision 6.1)
    data object ToggleCategoryGrouping : TodayUiEvent
    data class ToggleCategoryCollapsed(val categoryId: String?) : TodayUiEvent
    data class SelectCategoryFilter(val categoryId: String?) : TodayUiEvent
}

