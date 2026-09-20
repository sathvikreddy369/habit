package com.habit1.app.ui.history

import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitHistorySummary

data class HabitHistoryUiState(
    val habit: Habit? = null,
    val summary: HabitHistorySummary? = null,
    val records: List<HabitRecordEntity> = emptyList(),
    val isLoading: Boolean = false
)
