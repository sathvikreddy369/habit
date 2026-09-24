package com.habit1.app.ui.history

import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.DailyGoalHistoryAggregate
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
    val partialHabitsCount: Int = 0,
    val totalScheduledHabitsCount: Int,
    val completedGoalsCount: Int,
    val totalGoalsCount: Int,
    val hasReview: Boolean = false,
    val hasRecordedActivity: Boolean
)

data class HabitDayBreakdownItem(
    val habitId: String,
    val habitName: String,
    val status: CalendarDayStatus,
    val formattedProgress: String,
    val isPartial: Boolean = false,
    val isCompleted: Boolean = false
)

data class SelectedDateBreakdown(
    val date: LocalDate,
    val formattedDate: String,
    val habits: List<HabitDayBreakdownItem> = emptyList(),
    val goals: List<TodayGoalItem> = emptyList(),
    val dailyReview: DailyReview? = null,
    val historicalGoalAggregate: DailyGoalHistoryAggregate? = null
)


enum class HistoryViewMode {
    MONTH,
    YEAR
}

data class YearMonthSummaryItem(
    val yearMonth: YearMonth,
    val monthName: String,
    val totalHabitCompletions: Int,
    val totalHabitScheduledDays: Int,
    val habitCompletionRate: Float,
    val totalGoals: Int,
    val completedGoals: Int,
    val goalCompletionRate: Float,
    val isCurrentMonth: Boolean,
    val isFuture: Boolean
)

data class MonthSummary(
    val totalHabitCompletions: Int,
    val totalHabitScheduledDays: Int,
    val habitCompletionRate: Float,
    val totalGoals: Int,
    val completedGoals: Int,
    val goalCompletionRate: Float,
    val daysWithCompletions: Int = 0,
    val daysInMonth: Int = 30
)

data class HistoryUiState(
    val viewMode: HistoryViewMode = HistoryViewMode.MONTH,
    val selectedYear: Int,
    val selectedMonth: YearMonth,
    val formattedMonth: String,
    val selectedDate: LocalDate,
    val calendarDays: List<HistoryCalendarDayItem> = emptyList(),
    val selectedDateBreakdown: SelectedDateBreakdown? = null,
    val monthSummary: MonthSummary? = null,
    val yearlyOverview: List<YearMonthSummaryItem> = emptyList(),
    val isLoading: Boolean = false,
    val userMessage: String? = null
)
