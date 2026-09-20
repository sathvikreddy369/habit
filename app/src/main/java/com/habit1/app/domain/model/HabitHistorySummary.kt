package com.habit1.app.domain.model

/**
 * Pure domain summary of a habit's historical performance across an evaluated date range.
 *
 * All statistics are mathematically derived without opaque ratings, artificial health scores,
 * or synthetic motivational formulas.
 */
data class HabitHistorySummary(
    val habit: Habit,
    val streakResult: StreakResult,
    val totalRecordedDays: Int,
    val completedDaysCount: Int,
    val recordedIncompleteDaysCount: Int,
    val projectedMissedDaysCount: Int,
    val projectedRestDaysCount: Int,
    val totalRecordedVolume: Double,
    val averageOnCompletedDays: Double?,
    val averageOnRecordedDays: Double?,
    val historyDays: List<HabitHistoryDay>
)
