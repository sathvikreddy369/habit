package com.habit1.app.ui.history

import java.time.LocalDate

sealed interface HistoryUiEvent {
    data class SelectDate(val date: LocalDate) : HistoryUiEvent
    data object PreviousMonth : HistoryUiEvent
    data object NextMonth : HistoryUiEvent
}
