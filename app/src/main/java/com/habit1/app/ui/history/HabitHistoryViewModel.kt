package com.habit1.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class HabitHistoryViewModel(
    private val habitId: String,
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val evaluateHabitHistory: EvaluateHabitHistoryUseCase = EvaluateHabitHistoryUseCase(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private val today = DateTimeUtils.today(zoneId)

    val uiState: StateFlow<HabitHistoryUiState> = combine(
        habitRepository.observeHabitById(habitId),
        flow {
            emit(habitRecordRepository.getRecordsForHabit(habitId))
        }
    ) { habitEntity, recordEntities ->
        if (habitEntity == null) {
            HabitHistoryUiState(isLoading = false)
        } else {
            val domainHabit = habitEntity.toDomain()
            val domainRecords = recordEntities.map { it.toDomain() }
            val creationDate = DateTimeUtils.toLocalDate(domainHabit.createdAt, zoneId)

            val summary = evaluateHabitHistory.execute(
                habit = domainHabit,
                records = domainRecords,
                startDate = creationDate,
                endDate = today,
                todayDate = today,
                zoneId = zoneId
            )

            HabitHistoryUiState(
                habit = domainHabit,
                summary = summary,
                records = recordEntities.sortedByDescending { it.date },
                isLoading = false
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HabitHistoryUiState(isLoading = true)
    )

    class Factory(
        private val habitId: String,
        private val habitRepository: HabitRepository,
        private val habitRecordRepository: HabitRecordRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HabitHistoryViewModel::class.java)) {
                return HabitHistoryViewModel(habitId, habitRepository, habitRecordRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
