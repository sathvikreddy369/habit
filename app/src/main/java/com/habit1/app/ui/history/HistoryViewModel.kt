package com.habit1.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyReviewRepository
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import com.habit1.app.domain.usecase.EvaluateMeasurementUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import com.habit1.app.ui.today.TodayGoalItem
import com.habit1.app.ui.today.TodaySubtaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val dailyGoalRepository: DailyGoalRepository,
    private val dailyReviewRepository: DailyReviewRepository? = null,
    private val evaluateHabitHistory: EvaluateHabitHistoryUseCase = EvaluateHabitHistoryUseCase(),
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase(),
    private val evaluateMeasurement: EvaluateMeasurementUseCase = EvaluateMeasurementUseCase(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val today = DateTimeUtils.today(zoneId)
    private val selectedMonthFlow = MutableStateFlow(YearMonth.from(today))
    private val selectedDateFlow = MutableStateFlow(today)

    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())

    val uiState: StateFlow<HistoryUiState> = selectedMonthFlow.flatMapLatest { month ->
        val firstDay = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        val startDateStr = DateTimeUtils.formatDate(firstDay)
        val endDateStr = DateTimeUtils.formatDate(lastDay)

        val reviewsFlow = dailyReviewRepository?.observeReviewsForDateRange(startDateStr, endDateStr) ?: flowOf(emptyList())

        combine(
            habitRepository.observeAllHabits(), // Includes archived habits
            habitRecordRepository.observeRecordsForDateRange(startDateStr, endDateStr),
            dailyGoalRepository.observeGoalsForDateRange(startDateStr, endDateStr),
            reviewsFlow,
            selectedDateFlow
        ) { allHabitEntities, recordEntities, goalEntities, reviewEntities, selectedDate ->

            val allHabits = allHabitEntities.map { it.toDomain() }
            val recordsByDate = recordEntities.groupBy { it.date }
            val goalsByDate = goalEntities.groupBy { it.goal.targetDate }
            val reviewsByDate = reviewEntities.associateBy { it.date }

            // 1. Build calendar day items
            val calendarDays = mutableListOf<HistoryCalendarDayItem>()
            var dayCursor = firstDay
            var monthHabitCompletions = 0
            var monthHabitScheduledDays = 0
            var monthTotalGoals = 0
            var monthCompletedGoals = 0

            while (!dayCursor.isAfter(lastDay)) {
                val dateStr = DateTimeUtils.formatDate(dayCursor)
                val dayRecords = recordsByDate[dateStr] ?: emptyList()
                val dayGoals = goalsByDate[dateStr] ?: emptyList()
                val hasReviewForDay = reviewsByDate.containsKey(dateStr)

                // Count scheduled habits for dayCursor
                val scheduledForDay = allHabits.filter { habit ->
                    val creationDate = DateTimeUtils.toLocalDate(habit.createdAt, zoneId)
                    !dayCursor.isBefore(creationDate) && !habit.isPaused && evaluateSchedule.isScheduledOn(habit, dayCursor, zoneId)
                }

                val completedHabitsForDay = dayRecords.count { it.isCompleted }
                val partialHabitsForDay = dayRecords.count { !it.isCompleted && it.actualValue > 0.0 }
                val completedGoalsForDay = dayGoals.count { it.goal.isCompleted }

                calendarDays.add(
                    HistoryCalendarDayItem(
                        date = dayCursor,
                        isToday = dayCursor == today,
                        isSelected = dayCursor == selectedDate,
                        isCurrentMonth = true,
                        completedHabitsCount = completedHabitsForDay,
                        partialHabitsCount = partialHabitsForDay,
                        totalScheduledHabitsCount = scheduledForDay.size,
                        completedGoalsCount = completedGoalsForDay,
                        totalGoalsCount = dayGoals.size,
                        hasReview = hasReviewForDay,
                        hasRecordedActivity = dayRecords.isNotEmpty() || dayGoals.isNotEmpty() || hasReviewForDay
                    )
                )

                // Accumulate month totals (exclude future dates from scheduled denominator)
                if (!dayCursor.isAfter(today)) {
                    monthHabitCompletions += completedHabitsForDay
                    monthHabitScheduledDays += scheduledForDay.size
                }
                monthTotalGoals += dayGoals.size
                monthCompletedGoals += completedGoalsForDay

                dayCursor = dayCursor.plusDays(1)
            }

            // 2. Build selected date breakdown
            val selectedDateStr = DateTimeUtils.formatDate(selectedDate)
            val selectedDayRecords = recordsByDate[selectedDateStr]?.associateBy { it.habitId } ?: emptyMap()
            val selectedDayGoals = goalsByDate[selectedDateStr] ?: emptyList()
            val selectedDayReview = reviewsByDate[selectedDateStr]

            val habitBreakdown = allHabits.mapNotNull { habit ->
                val creationDate = DateTimeUtils.toLocalDate(habit.createdAt, zoneId)
                if (selectedDate.isBefore(creationDate)) {
                    // Pre-creation, omit from daily breakdown or render PreCreation
                    null
                } else {
                    val record = selectedDayRecords[habit.id]
                    val isCompleted = record != null && record.isCompleted
                    val isPartial = record != null && !record.isCompleted && record.actualValue > 0.0
                    val isScheduled = evaluateSchedule.isScheduledOn(habit, selectedDate, zoneId)

                    val status: CalendarDayStatus = when {
                        selectedDate.isAfter(today) -> CalendarDayStatus.Future
                        habit.isPaused -> CalendarDayStatus.Paused
                        isCompleted -> CalendarDayStatus.Completed(
                            actualValue = record!!.actualValue,
                            targetValue = record.targetValue,
                            unit = record.unit,
                            measurementType = record.measurementType
                        )
                        record != null -> CalendarDayStatus.RecordedIncomplete(
                            actualValue = record.actualValue,
                            targetValue = record.targetValue,
                            unit = record.unit,
                            measurementType = record.measurementType
                        )
                        isScheduled -> CalendarDayStatus.ProjectedMissed
                        else -> CalendarDayStatus.ProjectedRest
                    }

                    val formattedProgress = if (record != null) {
                        if (record.measurementType == "BOOLEAN") {
                            if (record.isCompleted) "Completed" else "Not completed"
                        } else {
                            val actualStr = formatStatValue(record.actualValue)
                            val targetStr = formatStatValue(record.targetValue)
                            val unitStr = record.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                            val pct = if (record.targetValue > 0.0) {
                                ((record.actualValue / record.targetValue) * 100).toInt()
                            } else {
                                0
                            }
                            "$actualStr / $targetStr$unitStr • $pct%"
                        }
                    } else {
                        when (status) {
                            CalendarDayStatus.ProjectedMissed -> "Missed (Projected)"
                            CalendarDayStatus.ProjectedRest -> "Rest Day (Projected)"
                            CalendarDayStatus.Paused -> "Paused"
                            CalendarDayStatus.Future -> "Upcoming"
                            else -> "No record"
                        }
                    }

                    HabitDayBreakdownItem(
                        habitId = habit.id,
                        habitName = habit.name,
                        status = status,
                        formattedProgress = formattedProgress,
                        isPartial = isPartial,
                        isCompleted = isCompleted
                    )
                }
            }

            val goalBreakdown = selectedDayGoals.map { item ->
                val domain = item.toDomain()
                TodayGoalItem(
                    id = domain.id,
                    title = domain.title,
                    isCompleted = domain.isCompleted,
                    notes = domain.notes,
                    subtasks = domain.subtasks.map { s ->
                        TodaySubtaskItem(
                            id = s.id,
                            title = s.title,
                            isCompleted = s.isCompleted,
                            displayOrder = s.displayOrder
                        )
                    }
                )
            }

            val habitRate = if (monthHabitScheduledDays > 0) {
                ((monthHabitCompletions.toDouble() / monthHabitScheduledDays.toDouble()) * 100.0).toFloat().coerceIn(0.0f, 100.0f)
            } else 0.0f

            val goalRate = if (monthTotalGoals > 0) {
                ((monthCompletedGoals.toDouble() / monthTotalGoals.toDouble()) * 100.0).toFloat().coerceIn(0.0f, 100.0f)
            } else 0.0f

            HistoryUiState(
                selectedMonth = month,
                formattedMonth = month.format(monthFormatter),
                selectedDate = selectedDate,
                calendarDays = calendarDays,
                selectedDateBreakdown = SelectedDateBreakdown(
                    date = selectedDate,
                    formattedDate = selectedDate.format(dateFormatter),
                    habits = habitBreakdown,
                    goals = goalBreakdown,
                    dailyReview = selectedDayReview?.toDomain()
                ),
                monthSummary = MonthSummary(
                    totalHabitCompletions = monthHabitCompletions,
                    totalHabitScheduledDays = monthHabitScheduledDays,
                    habitCompletionRate = habitRate,
                    totalGoals = monthTotalGoals,
                    completedGoals = monthCompletedGoals,
                    goalCompletionRate = goalRate
                ),
                isLoading = false
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(
            selectedMonth = YearMonth.from(today),
            formattedMonth = YearMonth.from(today).format(monthFormatter),
            selectedDate = today,
            isLoading = true
        )
    )

    fun onEvent(event: HistoryUiEvent) {
        scope.launch {
            when (event) {
                is HistoryUiEvent.SelectDate -> {
                    selectedDateFlow.value = event.date
                }
                is HistoryUiEvent.PreviousMonth -> {
                    val prev = selectedMonthFlow.value.minusMonths(1)
                    selectedMonthFlow.value = prev
                    selectedDateFlow.value = prev.atDay(1)
                }
                is HistoryUiEvent.NextMonth -> {
                    val next = selectedMonthFlow.value.plusMonths(1)
                    selectedMonthFlow.value = next
                    selectedDateFlow.value = next.atDay(1)
                }
                is HistoryUiEvent.JumpToToday -> {
                    selectedMonthFlow.value = YearMonth.from(today)
                    selectedDateFlow.value = today
                }
            }
        }
    }

    private fun formatStatValue(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            "%.1f".format(Locale.US, value)
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val habitRecordRepository: HabitRecordRepository,
        private val dailyGoalRepository: DailyGoalRepository,
        private val dailyReviewRepository: DailyReviewRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                return HistoryViewModel(
                    habitRepository = habitRepository,
                    habitRecordRepository = habitRecordRepository,
                    dailyGoalRepository = dailyGoalRepository,
                    dailyReviewRepository = dailyReviewRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

