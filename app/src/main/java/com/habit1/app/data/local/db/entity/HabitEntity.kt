package com.habit1.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Authoritative definition of a tracked habit.
 */
@Entity(
    tableName = "habits",
    indices = [
        Index(value = ["is_archived", "display_order"]),
        Index(value = ["created_at"])
    ]
)
data class HabitEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /**
     * Measurement type: "BOOLEAN", "COUNT", "DURATION", "QUANTITY"
     */
    @ColumnInfo(name = "measurement_type")
    val measurementType: String,

    /**
     * Expected target value (e.g. 1.0 for boolean, 50.0 for count, 30.0 for minutes, 2.5 for liters).
     */
    @ColumnInfo(name = "target_value")
    val targetValue: Double = 1.0,

    /**
     * Optional unit descriptor (e.g. "pushups", "pages", "min", "km", "L").
     */
    @ColumnInfo(name = "unit")
    val unit: String? = null,

    /**
     * Schedule type: "DAILY", "SPECIFIC_DAYS", "INTERVAL"
     */
    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,

    /**
     * Schedule configuration encoded as JSON string (e.g. {"days":[1,2,3,4,5]} or {"everyNDays":2,"anchorDate":"2026-01-01"}).
     */
    @ColumnInfo(name = "schedule_config")
    val scheduleConfig: String,

    /**
     * Optional time-of-day reminder in ISO "HH:mm" format (e.g. "08:00").
     */
    @ColumnInfo(name = "reminder_time")
    val reminderTime: String? = null,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "is_paused")
    val isPaused: Boolean = false,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "color_hex", defaultValue = "NULL")
    val colorHex: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
