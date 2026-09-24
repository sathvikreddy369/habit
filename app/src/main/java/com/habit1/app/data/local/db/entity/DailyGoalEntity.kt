package com.habit1.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An outcome or task targeted for a specific calendar date.
 */
@Entity(
    tableName = "daily_goals",
    indices = [
        Index(value = ["target_date", "display_order"]),
        Index(value = ["created_at"])
    ]
)
data class DailyGoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    /**
     * Calendar date for which this goal was planned in "YYYY-MM-DD" format.
     */
    @ColumnInfo(name = "target_date")
    val targetDate: String,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "reminder_time")
    val reminderTime: String? = null
)
