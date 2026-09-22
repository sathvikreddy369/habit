package com.habit1.app.domain.model

/**
 * Pure domain representation of historical Daily Goal completion counts for a single civil date.
 * Created when past completed goals are cleaned up.
 */
data class DailyGoalHistoryAggregate(
    val date: String,
    val completedCount: Int,
    val totalCount: Int
) {
    init {
        require(completedCount >= 0) { "completedCount ($completedCount) must be non-negative" }
        require(totalCount >= 0) { "totalCount ($totalCount) must be non-negative" }
        require(completedCount <= totalCount) { "completedCount ($completedCount) cannot exceed totalCount ($totalCount)" }
    }

    val completionRate: Float
        get() = if (totalCount > 0) {
            ((completedCount.toDouble() / totalCount.toDouble()) * 100.0).toFloat().coerceIn(0.0f, 100.0f)
        } else {
            0.0f
        }
}
