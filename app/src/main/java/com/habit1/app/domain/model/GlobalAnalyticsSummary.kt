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
    val targetFormatted: String
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
    val habitPerformances: List<HabitPerformanceItem>
)
