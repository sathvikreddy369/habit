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
}
