package com.habit1.app.domain.model

/**
 * Authoritative, read-only analytics summary for a habit over an [AnalyticsRange].
 *
 * Preserves Historical Truth (Product Principle #7):
 * - Factual records are never rewritten or fabricated.
 * - Schedules determine expected/missed days for unrecorded past dates, while recorded completions
 *   and values remain immutable fact.
 * - Missed days and recorded incomplete days are strictly separated.
 * - Current-day pending state does not depress completion rate prematurely before midnight.
 */
data class HabitAnalyticsSummary(
    val habit: Habit,
    val range: AnalyticsRange,

    // Schedule & Completion Aggregates within range
    val scheduledDays: Int,
    val eligibleScheduledDays: Int = scheduledDays,
    val completedDays: Int,
    val recordedIncompleteDays: Int,
    val missedDays: Int,
    val restDays: Int,
    val completionRate: Float,

    // Streaks (authoritative from CalculateStreaksUseCase)
    val currentStreak: Int,
    val longestStreak: Int,

    // Quantitative Statistics (non-null for Count, Duration, Quantity; null for BooleanChoice)
    val quantitativeStats: QuantitativeAnalyticsStats?,

    // Daily breakdown across the range (for heatmaps and trend graphs)
    val dailyBreakdown: List<HabitHistoryDay>
) {
    val totalActualValue: Double
        get() = quantitativeStats?.totalActualValue ?: 0.0

    val averageActualValue: Double?
        get() = quantitativeStats?.averageOnRecordedDays

    val targetAchievementRate: Float?
        get() = quantitativeStats?.targetAchievementRate
}

/**
 * Quantitative metrics for habits with Count, Duration, or Quantity measurements.
 *
 * All numbers preserve their domain meaning without artificial score normalizations.
 */
data class QuantitativeAnalyticsStats(
    /**
     * Total recorded value across all records in the evaluated range.
     * Preserves exact recorded amounts (e.g. 90 minutes remains 90, not clamped to 60 target).
     */
    val totalActualValue: Double,

    /**
     * Average actual value on days where the habit was completed (actualValue >= target).
     * Null if no completed records exist in the range.
     */
    val averageOnCompletedDays: Double?,

    /**
     * Average actual value on all days where a record was logged (completed or incomplete).
     * Null if no records were logged in the range.
     */
    val averageOnRecordedDays: Double?,

    /**
     * Total count of distinct calendar days with a logged record in the range.
     */
    val recordedDaysCount: Int,

    /**
     * Total count of distinct calendar days where target was achieved in the range.
     */
    val completedDaysCount: Int,

    /**
     * Percentage of eligible scheduled days on which the target was achieved.
     * Equivalent to completionRate for quantitative habits.
     */
    val targetAchievementRate: Float,

    /**
     * Percentage of recorded attempts on which the target was achieved (completedDaysCount / recordedDaysCount).
     * Null if no records were logged in the range.
     */
    val successRateOnRecordedDays: Float?
)
