package com.habit1.app.ui.history

import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.HabitHistorySummary
import java.time.LocalDate

data class PeriodStat(
    val completed: Int = 0,
    val total: Int = 7
)

data class HabitHistoryUiState(
    val habit: Habit? = null,
    val summary: HabitHistorySummary? = null,
    val analyticsSummary: HabitAnalyticsSummary? = null,
    val records: List<HabitRecordEntity> = emptyList(),
    val selectedPreset: HeatmapRangePreset = HeatmapRangePreset.THIS_WEEK,
    val currentRange: AnalyticsRange = AnalyticsRange.ofDaysEndingAt(LocalDate.now(), 7),
    val formattedRange: String = "",
    val canNavigateNext: Boolean = false,
    val isCurrentRange: Boolean = true,
    val selectedDayDetail: HabitHistoryDay? = null,
    val weekStat: PeriodStat = PeriodStat(0, 7),
    val monthStat: PeriodStat = PeriodStat(0, 30),
    val yearStat: PeriodStat = PeriodStat(0, 365),
    val isLoading: Boolean = false
)
