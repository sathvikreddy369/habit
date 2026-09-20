package com.habit1.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.mapper.EntityMappers.toEntity
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.model.StreakResult
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.EvaluateMeasurementUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
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
import java.util.UUID

/**
 * ViewModel for the Today screen.
 * Consumes pure domain use cases and exposes a single immutable StateFlow<TodayUiState>.
 */
class TodayViewModel(
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val dailyGoalRepository: DailyGoalRepository,
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase(),
    private val calculateStreaks: CalculateStreaksUseCase = CalculateStreaksUseCase(),
    private val evaluateMeasurement: EvaluateMeasurementUseCase = EvaluateMeasurementUseCase(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val currentDateFlow = MutableStateFlow(DateTimeUtils.today(zoneId))
    private val userMessageFlow = MutableStateFlow<String?>(null)
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = currentDateFlow.flatMapLatest { date ->
        val dateString = DateTimeUtils.formatDate(date)

        combine(
            habitRepository.observeActiveHabits(),
            habitRecordRepository.observeRecordsForDate(dateString),
            dailyGoalRepository.observeGoalsForDate(dateString),
            userMessageFlow
        ) { activeEntities, recordEntities, goalWithSubtasksList, message ->
            val habits = activeEntities.map { it.toDomain() }
            val recordsMap = recordEntities.associateBy { it.habitId }

            // 1. Filter habits expected for this civil date
            val scheduledHabits = habits.filter { habit ->
                evaluateSchedule.isScheduledOn(habit, date, zoneId)
            }

            // 2. Map to UI items
            val habitUiItems = scheduledHabits.map { habit ->
                val recordEntity = recordsMap[habit.id]
                val record = recordEntity?.toDomain()

                val actualValue = record?.actualValue ?: 0.0
                val isCompleted = record?.isCompleted ?: false
                val targetValue = when (val m = habit.measurement) {
                    is MeasurementType.BooleanChoice -> 1.0
                    is MeasurementType.Count -> m.target.toDouble()
                    is MeasurementType.Duration -> m.targetMinutes.toDouble()
                    is MeasurementType.Quantity -> m.target
                }
                val unit = when (val m = habit.measurement) {
                    is MeasurementType.BooleanChoice -> null
                    is MeasurementType.Count -> m.unit
                    is MeasurementType.Duration -> "mins"
                    is MeasurementType.Quantity -> m.unit
                }

                val progressRatio = evaluateMeasurement.progressRatio(habit.measurement, actualValue)
                val formattedProgress = evaluateMeasurement.formatProgress(habit.measurement, actualValue)

                // Retrieve all past records for streak calculation
                val habitHistory = habitRecordRepository.getRecordsForHabit(habit.id).map { it.toDomain() }
                val streak = calculateStreaks.execute(habit, habitHistory, date, zoneId)

                TodayHabitItem(
                    id = habit.id,
                    name = habit.name,
                    description = habit.description,
                    measurementType = habit.measurement,
                    targetValue = targetValue,
                    actualValue = actualValue,
                    unit = unit,
                    isCompleted = isCompleted,
                    progressRatio = progressRatio,
                    formattedProgress = formattedProgress,
                    streakResult = streak
                )
            }

            // 3. Map goals
            val goalUiItems = goalWithSubtasksList.map { item ->
                val domainGoal = item.toDomain()
                TodayGoalItem(
                    id = domainGoal.id,
                    title = domainGoal.title,
                    isCompleted = domainGoal.isCompleted,
                    subtasks = domainGoal.subtasks.map { subtask ->
                        TodaySubtaskItem(
                            id = subtask.id,
                            title = subtask.title,
                            isCompleted = subtask.isCompleted
                        )
                    },
                    notes = domainGoal.notes
                )
            }

            // 4. Summaries
            val completedHabitsCount = habitUiItems.count { it.isCompleted }
            val totalHabitsCount = habitUiItems.size
            val completedGoalsCount = goalUiItems.count { it.isCompleted }
            val totalGoalsCount = goalUiItems.size

            val totalTasks = totalHabitsCount + totalGoalsCount
            val completedTasks = completedHabitsCount + completedGoalsCount
            val overallProgress = if (totalTasks > 0) {
                (completedTasks.toFloat() / totalTasks.toFloat()).coerceIn(0.0f, 1.0f)
            } else {
                0.0f
            }

            TodayUiState(
                currentDate = date,
                formattedDate = date.format(dateFormatter),
                habits = habitUiItems,
                goals = goalUiItems,
                completedHabitsCount = completedHabitsCount,
                totalScheduledHabitsCount = totalHabitsCount,
                completedGoalsCount = completedGoalsCount,
                totalGoalsCount = totalGoalsCount,
                overallProgress = overallProgress,
                isLoading = false,
                userMessage = message
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState(
            currentDate = DateTimeUtils.today(zoneId),
            formattedDate = DateTimeUtils.today(zoneId).format(dateFormatter),
            isLoading = true
        )
    )

    fun onEvent(event: TodayUiEvent) {
        viewModelScope.launch {
            when (event) {
                is TodayUiEvent.ToggleHabit -> handleToggleHabit(event.habitId)
                is TodayUiEvent.IncrementHabit -> handleAdjustHabit(event.habitId, increment = true)
                is TodayUiEvent.DecrementHabit -> handleAdjustHabit(event.habitId, increment = false)
                is TodayUiEvent.SetHabitValue -> handleSetHabitValue(event.habitId, event.value)
                is TodayUiEvent.ToggleGoal -> dailyGoalRepository.setGoalCompleted(
                    event.goalId,
                    !isGoalCompleted(event.goalId)
                )
                is TodayUiEvent.ToggleSubtask -> handleToggleSubtask(event.goalId, event.subtaskId)
                is TodayUiEvent.AddGoal -> handleAddGoal(event.title)
                is TodayUiEvent.AddHabitQuick -> handleAddHabitQuick(event.name, event.measurementType)
                is TodayUiEvent.RefreshDate -> currentDateFlow.value = DateTimeUtils.today(zoneId)
                is TodayUiEvent.DismissMessage -> userMessageFlow.value = null
            }
        }
    }

    private suspend fun handleToggleHabit(habitId: String) {
        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain()
        val dateString = DateTimeUtils.formatDate(currentDateFlow.value)
        val existingRecord = habitRecordRepository.getRecord(habitId, dateString)

        val isCurrentlyCompleted = existingRecord?.isCompleted ?: false
        val now = System.currentTimeMillis()

        val (newActualValue, newIsCompleted) = when (val m = habit.measurement) {
            is MeasurementType.BooleanChoice -> {
                if (isCurrentlyCompleted) Pair(0.0, false) else Pair(1.0, true)
            }
            is MeasurementType.Count -> {
                if (isCurrentlyCompleted) Pair(0.0, false) else Pair(m.target.toDouble(), true)
            }
            is MeasurementType.Duration -> {
                if (isCurrentlyCompleted) Pair(0.0, false) else Pair(m.targetMinutes.toDouble(), true)
            }
            is MeasurementType.Quantity -> {
                if (isCurrentlyCompleted) Pair(0.0, false) else Pair(m.target, true)
            }
        }

        val targetVal = when (val m = habit.measurement) {
            is MeasurementType.BooleanChoice -> 1.0
            is MeasurementType.Count -> m.target.toDouble()
            is MeasurementType.Duration -> m.targetMinutes.toDouble()
            is MeasurementType.Quantity -> m.target
        }

        val recordToSave = HabitRecordEntity(
            id = existingRecord?.id ?: UUID.randomUUID().toString(),
            habitId = habitId,
            date = dateString,
            actualValue = newActualValue,
            targetValue = targetVal,
            measurementType = habit.measurement.typeName,
            unit = habitEntity.unit,
            isCompleted = newIsCompleted,
            recordedAt = now
        )
        habitRecordRepository.recordProgress(recordToSave)
    }

    private suspend fun handleAdjustHabit(habitId: String, increment: Boolean) {
        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain()
        val dateString = DateTimeUtils.formatDate(currentDateFlow.value)
        val existingRecord = habitRecordRepository.getRecord(habitId, dateString)

        val currentVal = existingRecord?.actualValue ?: 0.0
        val step = evaluateMeasurement.defaultStep(habit.measurement)
        val targetVal = when (val m = habit.measurement) {
            is MeasurementType.BooleanChoice -> 1.0
            is MeasurementType.Count -> m.target.toDouble()
            is MeasurementType.Duration -> m.targetMinutes.toDouble()
            is MeasurementType.Quantity -> m.target
        }

        val newVal = if (increment) {
            currentVal + step
        } else {
            (currentVal - step).coerceAtLeast(0.0)
        }

        val isCompleted = evaluateMeasurement.isCompleted(habit.measurement, newVal)
        val now = System.currentTimeMillis()

        val recordToSave = HabitRecordEntity(
            id = existingRecord?.id ?: UUID.randomUUID().toString(),
            habitId = habitId,
            date = dateString,
            actualValue = newVal,
            targetValue = targetVal,
            measurementType = habit.measurement.typeName,
            unit = habitEntity.unit,
            isCompleted = isCompleted,
            recordedAt = now
        )
        habitRecordRepository.recordProgress(recordToSave)
    }

    private suspend fun handleSetHabitValue(habitId: String, value: Double) {
        val habitEntity = habitRepository.getHabitById(habitId) ?: return
        val habit = habitEntity.toDomain()
        val dateString = DateTimeUtils.formatDate(currentDateFlow.value)
        val existingRecord = habitRecordRepository.getRecord(habitId, dateString)

        val targetVal = when (val m = habit.measurement) {
            is MeasurementType.BooleanChoice -> 1.0
            is MeasurementType.Count -> m.target.toDouble()
            is MeasurementType.Duration -> m.targetMinutes.toDouble()
            is MeasurementType.Quantity -> m.target
        }
        val isCompleted = evaluateMeasurement.isCompleted(habit.measurement, value)
        val now = System.currentTimeMillis()

        val recordToSave = HabitRecordEntity(
            id = existingRecord?.id ?: UUID.randomUUID().toString(),
            habitId = habitId,
            date = dateString,
            actualValue = value.coerceAtLeast(0.0),
            targetValue = targetVal,
            measurementType = habit.measurement.typeName,
            unit = habitEntity.unit,
            isCompleted = isCompleted,
            recordedAt = now
        )
        habitRecordRepository.recordProgress(recordToSave)
    }

    private fun isGoalCompleted(goalId: String): Boolean {
        return uiState.value.goals.find { it.id == goalId }?.isCompleted ?: false
    }

    private suspend fun handleToggleSubtask(goalId: String, subtaskId: String) {
        val goal = uiState.value.goals.find { it.id == goalId } ?: return
        val subtask = goal.subtasks.find { it.id == subtaskId } ?: return
        dailyGoalRepository.setSubtaskCompleted(subtaskId, !subtask.isCompleted)
    }

    private suspend fun handleAddGoal(title: String) {
        if (title.isBlank()) return
        val dateString = DateTimeUtils.formatDate(currentDateFlow.value)
        val now = System.currentTimeMillis()
        val currentGoalsCount = uiState.value.goals.size

        val newGoal = DailyGoalEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            targetDate = dateString,
            isCompleted = false,
            displayOrder = currentGoalsCount,
            createdAt = now,
            updatedAt = now
        )
        dailyGoalRepository.createGoal(newGoal)
    }

    private suspend fun handleAddHabitQuick(name: String, measurementType: MeasurementType) {
        if (name.isBlank()) return
        val now = System.currentTimeMillis()
        val currentHabitsCount = habitRepository.getActiveHabitsList().size

        val habit = com.habit1.app.domain.model.Habit(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            measurement = measurementType,
            schedule = HabitSchedule.Daily,
            displayOrder = currentHabitsCount,
            createdAt = java.time.Instant.ofEpochMilli(now),
            updatedAt = java.time.Instant.ofEpochMilli(now)
        )
        habitRepository.createHabit(habit.toEntity())
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val habitRecordRepository: HabitRecordRepository,
        private val dailyGoalRepository: DailyGoalRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
                return TodayViewModel(habitRepository, habitRecordRepository, dailyGoalRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
