package com.habit1.app.ui.history

import com.habit1.app.domain.model.HabitHistoryDay

sealed interface HabitHistoryUiEvent {
    data class SelectPreset(val preset: HeatmapRangePreset) : HabitHistoryUiEvent
    data object PreviousRange : HabitHistoryUiEvent
    data object NextRange : HabitHistoryUiEvent
    data object ResetToToday : HabitHistoryUiEvent
    data class SelectDay(val day: HabitHistoryDay) : HabitHistoryUiEvent
    data object DismissDayDetail : HabitHistoryUiEvent
}
