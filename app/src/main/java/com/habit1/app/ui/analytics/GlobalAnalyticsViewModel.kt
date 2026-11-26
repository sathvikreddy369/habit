package com.habit1.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.GlobalAnalyticsSummary
import com.habit1.app.domain.usecase.ComputeGlobalAnalyticsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Standardized rolling UI presets for Global Analytics (Decision 5.1.2).
 */
enum class GlobalAnalyticsPreset(val label: String) {
    Last7Days("Last 7 Days"),
    Last30Days("Last 30 Days"),
    Last90Days("Last 90 Days"),
    Last365Days("Last 365 Days"),
    AllTime("All Time");

    companion object {
        val ThisWeek = Last7Days
        val ThisMonth = Last30Days
        val ThisYear = Last365Days
    }
}

data class GlobalAnalyticsUiState(
    val selectedPreset: GlobalAnalyticsPreset = GlobalAnalyticsPreset.Last30Days,
    val summary: GlobalAnalyticsSummary? = null,
    val isLoading: Boolean = true
)

sealed interface GlobalAnalyticsUiEvent {
    data class SelectPreset(val preset: GlobalAnalyticsPreset) : GlobalAnalyticsUiEvent
    data object Refresh : GlobalAnalyticsUiEvent
}

class GlobalAnalyticsViewModel(
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val computeGlobalAnalyticsUseCase: ComputeGlobalAnalyticsUseCase,
    private val todayDateProvider: () -> LocalDate = { LocalDate.now() },
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalAnalyticsUiState())
    val uiState: StateFlow<GlobalAnalyticsUiState> = _uiState.asStateFlow()

    private val allHabitsFlow = MutableStateFlow<List<HabitEntity>>(emptyList())

    init {
        viewModelScope.launch {
            habitRepository.observeAllHabits().collectLatest { habits ->
                allHabitsFlow.value = habits
                computeAnalytics()
            }
        }
    }

    fun onEvent(event: GlobalAnalyticsUiEvent) {
        when (event) {
            is GlobalAnalyticsUiEvent.SelectPreset -> {
                _uiState.value = _uiState.value.copy(selectedPreset = event.preset)
                computeAnalytics()
            }
            is GlobalAnalyticsUiEvent.Refresh -> {
                computeAnalytics()
            }
        }
    }

    private fun computeAnalytics() {
        viewModelScope.launch(defaultDispatcher) {
            val todayDate = todayDateProvider()
            val allHabits = allHabitsFlow.value.map { it.toDomain() }
            val earliestStart = allHabits.minOfOrNull { it.startDate } ?: todayDate.minusYears(1)

            val range = when (_uiState.value.selectedPreset) {
                GlobalAnalyticsPreset.Last7Days -> {
                    val monday = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val sunday = monday.plusDays(6)
                    AnalyticsRange(monday, sunday)
                }
                GlobalAnalyticsPreset.Last30Days -> {
                    val start = todayDate.withDayOfMonth(1)
                    val end = todayDate.withDayOfMonth(todayDate.lengthOfMonth())
                    AnalyticsRange(start, end)
                }
                GlobalAnalyticsPreset.Last90Days -> AnalyticsRange.ofDaysEndingAt(todayDate, 90)
                GlobalAnalyticsPreset.Last365Days -> {
                    val start = todayDate.withDayOfYear(1)
                    val end = todayDate.withDayOfYear(todayDate.lengthOfYear())
                    AnalyticsRange(start, end)
                }
                GlobalAnalyticsPreset.AllTime -> {
                    val start = if (earliestStart.isAfter(todayDate)) todayDate else earliestStart
                    AnalyticsRange(start, todayDate)
                }
            }

            val allIds = allHabits.map { it.id }
            val recordsEntities = habitRecordRepository.getRecordsForHabits(allIds)
            val recordsByHabit = recordsEntities
                .map { it.toDomain() }
                .groupBy { it.habitId }

            // Decision 5.3: Include archived habits in historical analytics for their active periods
            val summary = computeGlobalAnalyticsUseCase.execute(
                habits = allHabits,
                recordsByHabit = recordsByHabit,
                range = range,
                todayDate = todayDate,
                includeArchived = true
            )

            _uiState.value = _uiState.value.copy(
                summary = summary,
                isLoading = false
            )
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val habitRecordRepository: HabitRecordRepository,
        private val computeGlobalAnalyticsUseCase: ComputeGlobalAnalyticsUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GlobalAnalyticsViewModel(
                habitRepository = habitRepository,
                habitRecordRepository = habitRecordRepository,
                computeGlobalAnalyticsUseCase = computeGlobalAnalyticsUseCase
            ) as T
        }
    }
}
