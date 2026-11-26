package com.habit1.app.ui.history

import com.habit1.app.domain.model.HabitHistoryDay
import java.time.LocalDate

sealed interface HabitHistoryUiEvent {
    data class SelectPreset(val preset: HeatmapRangePreset) : HabitHistoryUiEvent
    data object PreviousRange : HabitHistoryUiEvent
    data object NextRange : HabitHistoryUiEvent
    data object ResetToToday : HabitHistoryUiEvent
    data class SelectDay(val day: HabitHistoryDay) : HabitHistoryUiEvent
    data object DismissDayDetail : HabitHistoryUiEvent
    data object DeleteHabit : HabitHistoryUiEvent

    // Phase 2: Historical record editing actions
    data class CompleteToTarget(val date: LocalDate) : HabitHistoryUiEvent
    data class SetHabitValue(val date: LocalDate, val value: Double) : HabitHistoryUiEvent
    data class MarkSkipped(val date: LocalDate, val notes: String? = null) : HabitHistoryUiEvent
    data class MarkMissed(val date: LocalDate, val notes: String? = null) : HabitHistoryUiEvent
    data class ClearRecord(val date: LocalDate) : HabitHistoryUiEvent
    data class SaveNotes(val date: LocalDate, val notes: String?) : HabitHistoryUiEvent
}
