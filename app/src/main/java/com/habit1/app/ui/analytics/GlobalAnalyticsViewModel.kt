package com.habit1.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.GlobalAnalyticsSummary
import com.habit1.app.domain.usecase.ComputeGlobalAnalyticsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

import kotlinx.coroutines.CoroutineDispatcher

enum class GlobalAnalyticsPreset(val label: String) {
    ThisWeek("This Week"),
    ThisMonth("This Month"),
    ThisYear("This Year")
}

data class GlobalAnalyticsUiState(
    val selectedPreset: GlobalAnalyticsPreset = GlobalAnalyticsPreset.ThisMonth,
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

    init {
        viewModelScope.launch {
            habitRepository.observeActiveHabits().collectLatest {
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
            val range = when (_uiState.value.selectedPreset) {
                GlobalAnalyticsPreset.ThisWeek -> {
                    val monday = todayDate.with(DayOfWeek.MONDAY)
                    val sunday = todayDate.with(DayOfWeek.SUNDAY)
                    AnalyticsRange(monday, sunday)
                }
                GlobalAnalyticsPreset.ThisMonth -> {
                    val start = todayDate.withDayOfMonth(1)
                    val end = todayDate.withDayOfMonth(todayDate.lengthOfMonth())
                    AnalyticsRange(start, end)
                }
                GlobalAnalyticsPreset.ThisYear -> {
                    val start = todayDate.withDayOfYear(1)
                    val end = todayDate.withDayOfYear(todayDate.lengthOfYear())
                    AnalyticsRange(start, end)
                }
            }

            val activeHabits = habitRepository.getActiveHabitsList().map { it.toDomain() }
            val activeIds = activeHabits.map { it.id }
            val recordsEntities = habitRecordRepository.getRecordsForHabits(activeIds)
            val recordsByHabit = recordsEntities
                .map { it.toDomain() }
                .groupBy { it.habitId }

            val summary = computeGlobalAnalyticsUseCase.execute(
                habits = activeHabits,
                recordsByHabit = recordsByHabit,
                range = range,
                todayDate = todayDate
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
