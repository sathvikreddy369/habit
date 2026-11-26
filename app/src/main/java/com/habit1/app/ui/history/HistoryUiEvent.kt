package com.habit1.app.ui.history

import java.time.LocalDate

sealed interface HistoryUiEvent {
    data class SelectDate(val date: LocalDate) : HistoryUiEvent
    data object PreviousMonth : HistoryUiEvent
    data object NextMonth : HistoryUiEvent
    data object JumpToToday : HistoryUiEvent
    data object PreviousYear : HistoryUiEvent
    data object NextYear : HistoryUiEvent
    data class SelectYear(val year: Int) : HistoryUiEvent
    data object JumpToCurrentYear : HistoryUiEvent
    data class ToggleViewMode(val mode: HistoryViewMode) : HistoryUiEvent
    data class SelectMonthFromYear(val yearMonth: java.time.YearMonth) : HistoryUiEvent
    data class MoveGoalToToday(val goalId: String) : HistoryUiEvent
    data object DismissMessage : HistoryUiEvent

    // Phase 2: Historical record editing actions
    data class CompleteToTarget(val habitId: String, val date: LocalDate) : HistoryUiEvent
    data class SetHabitValue(val habitId: String, val date: LocalDate, val value: Double) : HistoryUiEvent
    data class MarkSkipped(val habitId: String, val date: LocalDate, val notes: String? = null) : HistoryUiEvent
    data class MarkMissed(val habitId: String, val date: LocalDate, val notes: String? = null) : HistoryUiEvent
    data class ClearRecord(val habitId: String, val date: LocalDate) : HistoryUiEvent
    data class SaveNotes(val habitId: String, val date: LocalDate, val notes: String?) : HistoryUiEvent
}
