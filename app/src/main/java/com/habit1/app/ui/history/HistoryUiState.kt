package com.habit1.app.ui.history

import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.DailyReview
import com.habit1.app.ui.today.TodayGoalItem
import java.time.LocalDate
import java.time.YearMonth

data class HistoryCalendarDayItem(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isCurrentMonth: Boolean,
    val completedHabitsCount: Int,
    val totalScheduledHabitsCount: Int,
    val completedGoalsCount: Int,
    val totalGoalsCount: Int,
    val hasRecordedActivity: Boolean
)

data class HabitDayBreakdownItem(
    val habitId: String,
    val habitName: String,
    val status: CalendarDayStatus,
    val formattedProgress: String
)

data class SelectedDateBreakdown(
    val date: LocalDate,
    val formattedDate: String,
    val habits: List<HabitDayBreakdownItem> = emptyList(),
    val goals: List<TodayGoalItem> = emptyList(),
    val dailyReview: DailyReview? = null
)


data class MonthSummary(
    val totalHabitCompletions: Int,
    val totalHabitScheduledDays: Int,
    val habitCompletionRate: Float,
    val totalGoals: Int,
    val completedGoals: Int,
    val goalCompletionRate: Float
)

data class HistoryUiState(
    val selectedMonth: YearMonth,
    val formattedMonth: String,
    val selectedDate: LocalDate,
    val calendarDays: List<HistoryCalendarDayItem> = emptyList(),
    val selectedDateBreakdown: SelectedDateBreakdown? = null,
    val monthSummary: MonthSummary? = null,
    val isLoading: Boolean = false
)
