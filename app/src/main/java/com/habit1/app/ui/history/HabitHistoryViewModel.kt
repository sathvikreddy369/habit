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
import java.time.DayOfWeek
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

    private val rangeQueryFlow = combine(selectedPresetFlow, anchorEndDateFlow) { preset, anchor ->
        val range = resolveRange(preset, anchor)
        val todayCurrentRange = resolveRange(preset, today)
        val canNavigateNext = range.startDate.isBefore(todayCurrentRange.startDate)
        val isCurrent = (range.startDate == todayCurrentRange.startDate && range.endDate == todayCurrentRange.endDate)
        val formattedRange = formatRange(preset, range)
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
            habitRecordRepository.observeRecordsForHabit(habitId),
            selectedDayDetailFlow
        ) { habitEntity, allRecordEntities, selectedDayDetail ->
            if (habitEntity == null) {
                HabitHistoryUiState(isLoading = false)
            } else {
                val domainHabit = habitEntity.toDomain()
                val allDomainRecords = allRecordEntities.map { it.toDomain() }
                val recordsInRange = allRecordEntities.filter {
                    val d = LocalDate.parse(it.date)
                    !d.isBefore(config.range.startDate) && !d.isAfter(config.range.endDate)
                }

                val analyticsSummary = computeHabitAnalytics.execute(
                    habit = domainHabit,
                    records = allDomainRecords,
                    range = config.range,
                    todayDate = today,
                    zoneId = zoneId
                )

                // Preserved for backward compatibility with existing tests
                val summary = evaluateHabitHistory.execute(
                    habit = domainHabit,
                    records = allDomainRecords,
                    startDate = config.range.startDate,
                    endDate = config.range.endDate,
                    todayDate = today,
                    zoneId = zoneId
                )

                HabitHistoryUiState(
                    habit = domainHabit,
                    summary = summary,
                    analyticsSummary = analyticsSummary,
                    records = recordsInRange.sortedByDescending { it.date },
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
                        HeatmapRangePreset.THIS_WEEK -> currentAnchor.minusWeeks(1)
                        HeatmapRangePreset.THIS_MONTH -> currentAnchor.minusMonths(1)
                        HeatmapRangePreset.THIS_YEAR -> currentAnchor.minusYears(1)
                    }
                    anchorEndDateFlow.value = newAnchor
                }

                is HabitHistoryUiEvent.NextRange -> {
                    val preset = selectedPresetFlow.value
                    val currentAnchor = anchorEndDateFlow.value
                    val shifted = when (preset) {
                        HeatmapRangePreset.THIS_WEEK -> currentAnchor.plusWeeks(1)
                        HeatmapRangePreset.THIS_MONTH -> currentAnchor.plusMonths(1)
                        HeatmapRangePreset.THIS_YEAR -> currentAnchor.plusYears(1)
                    }
                    val todayRange = resolveRange(preset, today)
                    val shiftedRange = resolveRange(preset, shifted)
                    anchorEndDateFlow.value = if (shiftedRange.startDate.isAfter(todayRange.startDate)) today else shifted
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

                is HabitHistoryUiEvent.DeleteHabit -> {
                    habitRepository.deleteHabit(habitId)
                }
            }
        }
    }

    private fun resolveRange(preset: HeatmapRangePreset, anchorEndDate: LocalDate): AnalyticsRange {
        return when (preset) {
            HeatmapRangePreset.THIS_WEEK -> {
                val monday = anchorEndDate.with(DayOfWeek.MONDAY)
                val sunday = anchorEndDate.with(DayOfWeek.SUNDAY)
                AnalyticsRange(monday, sunday)
            }
            HeatmapRangePreset.THIS_MONTH -> {
                val start = anchorEndDate.withDayOfMonth(1)
                val end = anchorEndDate.withDayOfMonth(anchorEndDate.lengthOfMonth())
                AnalyticsRange(start, end)
            }
            HeatmapRangePreset.THIS_YEAR -> {
                val start = anchorEndDate.withDayOfYear(1)
                val end = anchorEndDate.withDayOfYear(anchorEndDate.lengthOfYear())
                AnalyticsRange(start, end)
            }
        }
    }

    private fun formatRange(preset: HeatmapRangePreset, range: AnalyticsRange): String {
        return when (preset) {
            HeatmapRangePreset.THIS_WEEK -> {
                if (range.startDate.year == range.endDate.year) {
                    if (range.startDate.month == range.endDate.month) {
                        val month = range.startDate.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                        "$month ${range.startDate.dayOfMonth} – ${range.endDate.dayOfMonth}, ${range.endDate.year}"
                    } else {
                        val m1 = range.startDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                        val m2 = range.endDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
                        "$m1 – $m2"
                    }
                } else {
                    val m1 = range.startDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
                    val m2 = range.endDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
                    "$m1 – $m2"
                }
            }
            HeatmapRangePreset.THIS_MONTH -> {
                range.startDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            }
            HeatmapRangePreset.THIS_YEAR -> {
                range.startDate.format(DateTimeFormatter.ofPattern("yyyy", Locale.getDefault()))
            }
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
