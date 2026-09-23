package com.habit1.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HabitHistoryViewModel(
    private val habitId: String,
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val evaluateHabitHistory: EvaluateHabitHistoryUseCase = EvaluateHabitHistoryUseCase(),
    private val computeHabitAnalytics: ComputeHabitAnalyticsUseCase = ComputeHabitAnalyticsUseCase(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private val today = DateTimeUtils.today(zoneId)

    private val selectedPresetFlow = MutableStateFlow(HeatmapRangePreset.THIS_WEEK)
    private val anchorEndDateFlow = MutableStateFlow(today)
    private val selectedDayDetailFlow = MutableStateFlow<HabitHistoryDay?>(null)

    private val rangeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val yearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    private val rangeQueryFlow = combine(selectedPresetFlow, anchorEndDateFlow) { preset, anchor ->
        val range = resolveRange(preset, anchor)
        val canNavigateNext = anchor.isBefore(today)
        val isCurrent = anchor == today
        val formattedRange = formatRange(range)
        RangeConfig(
            preset = preset,
            range = range,
            formattedRange = formattedRange,
            canNavigateNext = canNavigateNext,
            isCurrentRange = isCurrent
        )
    }

    val uiState: StateFlow<HabitHistoryUiState> = rangeQueryFlow.flatMapLatest { config ->
        combine(
            habitRepository.observeHabitById(habitId),
            habitRecordRepository.observeRecordsForHabitInRange(habitId, config.range),
            selectedDayDetailFlow
        ) { habitEntity, recordEntities, selectedDayDetail ->
            if (habitEntity == null) {
                HabitHistoryUiState(isLoading = false)
            } else {
                val domainHabit = habitEntity.toDomain()
                val domainRecords = recordEntities.map { it.toDomain() }

                val analyticsSummary = computeHabitAnalytics.execute(
                    habit = domainHabit,
                    records = domainRecords,
                    range = config.range,
                    todayDate = today,
                    zoneId = zoneId
                )

                // Preserved for backward compatibility with existing tests
                val summary = evaluateHabitHistory.execute(
                    habit = domainHabit,
                    records = domainRecords,
                    startDate = config.range.startDate,
                    endDate = config.range.endDate,
                    todayDate = today,
                    zoneId = zoneId
                )

                HabitHistoryUiState(
                    habit = domainHabit,
                    summary = summary,
                    analyticsSummary = analyticsSummary,
                    records = recordEntities.sortedByDescending { it.date },
                    selectedPreset = config.preset,
                    currentRange = config.range,
                    formattedRange = config.formattedRange,
                    canNavigateNext = config.canNavigateNext,
                    isCurrentRange = config.isCurrentRange,
                    selectedDayDetail = selectedDayDetail,
                    isLoading = false
                )
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HabitHistoryUiState(isLoading = true)
    )

    fun onEvent(event: HabitHistoryUiEvent) {
        scope.launch {
            when (event) {
                is HabitHistoryUiEvent.SelectPreset -> {
                    selectedPresetFlow.value = event.preset
                    // Reset anchor to today when preset changes
                    anchorEndDateFlow.value = today
                }

                is HabitHistoryUiEvent.PreviousRange -> {
                    val preset = selectedPresetFlow.value
                    val currentAnchor = anchorEndDateFlow.value
                    val newAnchor = when (preset) {
                        HeatmapRangePreset.THIS_WEEK -> currentAnchor.minusDays(7)
                        HeatmapRangePreset.MONTHLY -> currentAnchor.minusMonths(1)
                        HeatmapRangePreset.YEARLY -> currentAnchor.minusYears(1)
                    }
                    anchorEndDateFlow.value = newAnchor
                }

                is HabitHistoryUiEvent.NextRange -> {
                    val preset = selectedPresetFlow.value
                    val currentAnchor = anchorEndDateFlow.value
                    val shifted = when (preset) {
                        HeatmapRangePreset.THIS_WEEK -> currentAnchor.plusDays(7)
                        HeatmapRangePreset.MONTHLY -> currentAnchor.plusMonths(1)
                        HeatmapRangePreset.YEARLY -> currentAnchor.plusYears(1)
                    }
                    anchorEndDateFlow.value = if (shifted.isAfter(today)) today else shifted
                }

                is HabitHistoryUiEvent.ResetToToday -> {
                    anchorEndDateFlow.value = today
                }

                is HabitHistoryUiEvent.SelectDay -> {
                    selectedDayDetailFlow.value = event.day
                }

                is HabitHistoryUiEvent.DismissDayDetail -> {
                    selectedDayDetailFlow.value = null
                }
            }
        }
    }

    private fun resolveRange(preset: HeatmapRangePreset, anchorEndDate: LocalDate): AnalyticsRange {
        return when (preset) {
            HeatmapRangePreset.THIS_WEEK -> AnalyticsRange.ofDaysEndingAt(anchorEndDate, 7)
            HeatmapRangePreset.MONTHLY -> {
                val start = anchorEndDate.withDayOfMonth(1)
                val end = anchorEndDate.withDayOfMonth(anchorEndDate.lengthOfMonth())
                AnalyticsRange(start, end)
            }
            HeatmapRangePreset.YEARLY -> {
                val start = anchorEndDate.withDayOfYear(1)
                val end = anchorEndDate.withDayOfYear(anchorEndDate.lengthOfYear())
                AnalyticsRange(start, end)
            }
        }
    }

    private fun formatRange(range: AnalyticsRange): String {
        return if (range.startDate.year == range.endDate.year) {
            if (range.startDate.month == range.endDate.month && range.startDate.dayOfMonth == 1 && range.endDate.dayOfMonth == range.endDate.lengthOfMonth()) {
                range.startDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            } else if (range.startDate.dayOfYear == 1 && range.endDate.dayOfYear == range.endDate.lengthOfYear()) {
                range.startDate.format(DateTimeFormatter.ofPattern("yyyy", Locale.getDefault()))
            } else {
                "${range.startDate.format(rangeFormatter)} — ${range.endDate.format(yearFormatter)}"
            }
        } else {
            "${range.startDate.format(yearFormatter)} — ${range.endDate.format(yearFormatter)}"
        }
    }

    private data class RangeConfig(
        val preset: HeatmapRangePreset,
        val range: AnalyticsRange,
        val formattedRange: String,
        val canNavigateNext: Boolean,
        val isCurrentRange: Boolean
    )

    class Factory(
        private val habitId: String,
        private val habitRepository: HabitRepository,
        private val habitRecordRepository: HabitRecordRepository,
        private val evaluateHabitHistory: EvaluateHabitHistoryUseCase = EvaluateHabitHistoryUseCase(),
        private val computeHabitAnalytics: ComputeHabitAnalyticsUseCase = ComputeHabitAnalyticsUseCase()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HabitHistoryViewModel::class.java)) {
                return HabitHistoryViewModel(
                    habitId = habitId,
                    habitRepository = habitRepository,
                    habitRecordRepository = habitRecordRepository,
                    evaluateHabitHistory = evaluateHabitHistory,
                    computeHabitAnalytics = computeHabitAnalytics
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
