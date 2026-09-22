package com.habit1.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable historical aggregate of Daily Goals on a past calendar date.
 * Created atomically during Daily Goal cleanup when detailed completed goals are removed.
 *
 * Preserves Historical Truth:
 * - date: Civil calendar date in "YYYY-MM-DD" format.
 * - completed_count: Total completed goals for this date.
 * - total_count: Total goals that existed for this date before cleanup.
 */
@Entity(tableName = "daily_goal_history_aggregates")
data class DailyGoalHistoryAggregateEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "completed_count")
    val completedCount: Int,

    @ColumnInfo(name = "total_count")
    val totalCount: Int
) {
    init {
        require(completedCount >= 0) { "completedCount ($completedCount) must be non-negative" }
        require(totalCount >= 0) { "totalCount ($totalCount) must be non-negative" }
        require(completedCount <= totalCount) { "completedCount ($completedCount) cannot exceed totalCount ($totalCount)" }
    }
}
