package com.habit1.app.ui.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.export.ExportFilter
import com.habit1.app.data.export.ExportFormat
import com.habit1.app.data.export.HabitDataExporter
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HabitHistoryViewModel(
    private val habitId: String,
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val evaluateHabitHistory: EvaluateHabitHistoryUseCase = EvaluateHabitHistoryUseCase(),
    private val computeHabitAnalytics: ComputeHabitAnalyticsUseCase = ComputeHabitAnalyticsUseCase(),
    private val evaluateMeasurement: com.habit1.app.domain.usecase.EvaluateMeasurementUseCase = com.habit1.app.domain.usecase.EvaluateMeasurementUseCase(),
    private val evaluateSchedule: com.habit1.app.domain.usecase.EvaluateScheduleUseCase = com.habit1.app.domain.usecase.EvaluateScheduleUseCase(),
    private val recordHabitProgress: com.habit1.app.domain.usecase.RecordHabitProgressUseCase =
        com.habit1.app.domain.usecase.RecordHabitProgressUseCase(habitRepository, habitRecordRepository, evaluateMeasurement, evaluateSchedule),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private val today = DateTimeUtils.today(zoneId)

    private val selectedPresetFlow = MutableStateFlow(HeatmapRangePreset.LAST_7_DAYS)
    private val anchorEndDateFlow = MutableStateFlow(today)
    private val selectedDayDetailFlow = MutableStateFlow<HabitHistoryDay?>(null)

    private val rangeQueryFlow = combine(selectedPresetFlow, anchorEndDateFlow) { preset, anchor ->
        val range = resolveRange(preset, anchor)
        val canNavigateNext = anchor.isBefore(today)
        val isCurrent = (anchor == today)
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

                // Accurate Calendar-based Period Stats:
                val monday = today.with(DayOfWeek.MONDAY)
                val sunday = today.with(DayOfWeek.SUNDAY)
                val weekCompleted = allDomainRecords.count {
                    it.isCompleted && !it.date.isBefore(monday) && !it.date.isAfter(sunday)
                }
                val weekStat = PeriodStat(completed = weekCompleted, total = 7)

                val monthStart = today.withDayOfMonth(1)
                val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
                val monthTotalDays = today.lengthOfMonth()
                val monthCompleted = allDomainRecords.count {
                    it.isCompleted && !it.date.isBefore(monthStart) && !it.date.isAfter(monthEnd)
                }
                val monthStat = PeriodStat(completed = monthCompleted, total = monthTotalDays)

                val yearStart = today.withDayOfYear(1)
                val yearEnd = today.withDayOfYear(today.lengthOfYear())
                val yearTotalDays = today.lengthOfYear()
                val yearCompleted = allDomainRecords.count {
                    it.isCompleted && !it.date.isBefore(yearStart) && !it.date.isAfter(yearEnd)
                }
                val yearStat = PeriodStat(completed = yearCompleted, total = yearTotalDays)

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
                    weekStat = weekStat,
                    monthStat = monthStat,
                    yearStat = yearStat,
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
                        HeatmapRangePreset.LAST_7_DAYS -> currentAnchor.minusWeeks(1)
                        HeatmapRangePreset.LAST_30_DAYS -> currentAnchor.minusMonths(1)
                        HeatmapRangePreset.LAST_90_DAYS -> currentAnchor.minusDays(90)
                        HeatmapRangePreset.LAST_365_DAYS -> currentAnchor.minusYears(1)
                        HeatmapRangePreset.ALL_TIME -> currentAnchor
                    }
                    anchorEndDateFlow.value = newAnchor
                }

                is HabitHistoryUiEvent.NextRange -> {
                    val preset = selectedPresetFlow.value
                    val currentAnchor = anchorEndDateFlow.value
                    val shifted = when (preset) {
                        HeatmapRangePreset.LAST_7_DAYS -> currentAnchor.plusWeeks(1)
                        HeatmapRangePreset.LAST_30_DAYS -> currentAnchor.plusMonths(1)
                        HeatmapRangePreset.LAST_90_DAYS -> currentAnchor.plusDays(90)
                        HeatmapRangePreset.LAST_365_DAYS -> currentAnchor.plusYears(1)
                        HeatmapRangePreset.ALL_TIME -> today
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

                is HabitHistoryUiEvent.DeleteHabit -> {
                    habitRepository.deleteHabit(habitId)
                }

                // Historical record editing actions
                is HabitHistoryUiEvent.CompleteToTarget -> {
                    recordHabitProgress.markCompleted(habitId, event.date, zoneId)
                }

                is HabitHistoryUiEvent.SetHabitValue -> {
                    recordHabitProgress.setActualValue(habitId, event.date, event.value, zoneId)
                }

                is HabitHistoryUiEvent.MarkSkipped -> {
                    recordHabitProgress.markSkipped(habitId, event.date, event.notes, zoneId)
                }

                is HabitHistoryUiEvent.MarkMissed -> {
                    recordHabitProgress.markMissed(habitId, event.date, event.notes, zoneId)
                }

                is HabitHistoryUiEvent.ClearRecord -> {
                    recordHabitProgress.clearRecord(habitId, event.date, zoneId)
                }

                is HabitHistoryUiEvent.SaveNotes -> {
                    val dateStr = DateTimeUtils.formatDate(event.date)
                    val existing = habitRecordRepository.getRecord(habitId, dateStr)
                    val trimmedNotes = event.notes?.trim()?.ifBlank { null }
                    if (existing != null) {
                        habitRecordRepository.recordProgress(existing.copy(notes = trimmedNotes))
                    } else {
                        recordHabitProgress.markMissed(habitId, event.date, notes = trimmedNotes, zoneId = zoneId)
                    }
                }
            }
        }
    }

    fun exportSingleHabit(
        uri: Uri,
        format: ExportFormat,
        startDate: LocalDate?,
        endDate: LocalDate?,
        context: Context,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val habitEntity = habitRepository.getHabitById(habitId)
                if (habitEntity == null) {
                    withContext(Dispatchers.Main) { onError("Habit not found") }
                    return@launch
                }
                val habit = habitEntity.toDomain()
                val recordsEntities = habitRecordRepository.getRecordsForHabits(listOf(habitId))
                val records = recordsEntities.map { it.toDomain() }

                val filter = ExportFilter(
                    habitIds = setOf(habitId),
                    startDate = startDate,
                    endDate = endDate,
                    format = format
                )

                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    withContext(Dispatchers.Main) { onError("Failed to open file destination.") }
                    return@launch
                }

                val count = when (format) {
                    ExportFormat.CSV -> HabitDataExporter.exportToCsv(
                        habits = listOf(habit),
                        recordsByHabit = mapOf(habitId to records),
                        categoryNames = emptyMap(),
                        filter = filter,
                        outputStream = outputStream
                    )
                    ExportFormat.JSON -> HabitDataExporter.exportToJson(
                        habits = listOf(habit),
                        recordsByHabit = mapOf(habitId to records),
                        categoryNames = emptyMap(),
                        filter = filter,
                        outputStream = outputStream
                    )
                }

                withContext(Dispatchers.Main) {
                    onSuccess("Exported $count records for ${habit.name}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Export failed: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    private fun resolveRange(preset: HeatmapRangePreset, anchorEndDate: LocalDate): AnalyticsRange {
        val anchor = if (anchorEndDate.isAfter(today)) today else anchorEndDate
        return when (preset) {
            HeatmapRangePreset.LAST_7_DAYS -> {
                // Strict Monday to Sunday week boundary (never mix prev/next weeks)
                val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6)
                AnalyticsRange(monday, sunday)
            }
            HeatmapRangePreset.LAST_30_DAYS -> {
                // Strict 1st to end of month boundary (never mix prev/next months)
                val start = anchor.withDayOfMonth(1)
                val end = anchor.withDayOfMonth(anchor.lengthOfMonth())
                AnalyticsRange(start, end)
            }
            HeatmapRangePreset.LAST_90_DAYS -> AnalyticsRange.ofDaysEndingAt(anchor, 90)
            HeatmapRangePreset.LAST_365_DAYS -> {
                // Strict Jan 1st to Dec 31st boundary
                val start = anchor.withDayOfYear(1)
                val end = anchor.withDayOfYear(anchor.lengthOfYear())
                AnalyticsRange(start, end)
            }
            HeatmapRangePreset.ALL_TIME -> {
                val start = anchor.minusYears(1)
                AnalyticsRange(start, anchor)
            }
        }
    }

    private fun formatRange(preset: HeatmapRangePreset, range: AnalyticsRange): String {
        return if (range.startDate.year == range.endDate.year) {
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
