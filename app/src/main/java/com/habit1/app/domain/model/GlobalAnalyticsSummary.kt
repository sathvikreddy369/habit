package com.habit1.app.domain.model

/**
 * Performance summary for a single habit displayed within Global Analytics.
 */
data class HabitPerformanceItem(
    val habitId: String,
    val habitName: String,
    val measurementType: MeasurementType,
    val completionRate: Float,
    val currentStreak: Int,
    val longestStreak: Int,
    val scheduledDays: Int,
    val completedDays: Int,
    val recentTrendDelta: Float?,
    val isPaused: Boolean,
    val targetFormatted: String,
    val isArchived: Boolean = false
)

/**
 * Top streak holder item across active habits.
 */
data class StreakLeaderItem(
    val habitId: String,
    val habitName: String,
    val currentStreak: Int,
    val longestStreak: Int
)

/**
 * Complete immutable aggregate analytics summary across all active habits for a given [AnalyticsRange].
 */
data class GlobalAnalyticsSummary(
    val range: AnalyticsRange,
    val globalCompletionRate: Float,
    val totalCompletedDays: Int,
    val totalScheduledDays: Int,
    val activeHabitsCount: Int,
    val streakLeaders: List<StreakLeaderItem>,
    val habitPerformances: List<HabitPerformanceItem>,
    val recordBreakdown: RecordBreakdown = RecordBreakdown(
        completedCount = totalCompletedDays,
        partialCount = 0,
        skippedCount = 0,
        missedCount = (totalScheduledDays - totalCompletedDays).coerceAtLeast(0),
        unrecordedCount = 0,
        restCount = 0,
        pausedCount = 0,
        totalScheduledInstances = totalScheduledDays,
        totalRecordedInstances = totalCompletedDays
    ),
    val dailyTrend: List<DailyCompletionPoint> = emptyList(),
    val weekdayAdherence: List<WeekdayAdherence> = emptyList(),
    val periodComparison: PeriodComparison? = null,
    val insights: List<HabitInsight> = emptyList()
)
