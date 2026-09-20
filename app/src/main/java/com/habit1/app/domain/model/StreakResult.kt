package com.habit1.app.domain.model

/**
 * Deterministic and explainable streak and consistency metrics.
 *
 * Strict Product Rule (Engineering Rules #3 & ADR-08):
 * - No opaque "productivity scores" or synthetic gamified algorithms.
 * - All metrics are mathematically derived from authoritative scheduled days and records.
 */
data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCompletions: Int,
    val totalScheduledDays: Int,
    val completionRate: Float
)
