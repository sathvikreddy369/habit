package com.habit1.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Authoritative historical schedule version for a habit.
 *
 * Preserves Historical Truth (Product Principle #7):
 * - When a user alters a habit's schedule (e.g. from DAILY to SPECIFIC_DAYS), previous dates
 *   continue to evaluate against the schedule version that was legally active on that date.
 * - effective_from and effective_to define non-overlapping inclusive civil date ranges.
 * - null effective_to represents the current active schedule configuration.
 */
@Entity(
    tableName = "habit_schedules",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habit_id", "effective_from"])
    ]
)
data class HabitScheduleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "habit_id")
    val habitId: String,

    /**
     * Schedule type: "DAILY", "SPECIFIC_DAYS", "INTERVAL"
     */
    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,

    /**
     * Serialized schedule configuration JSON string.
     */
    @ColumnInfo(name = "schedule_config")
    val scheduleConfig: String,

    /**
     * Inclusive start civil date in ISO-8601 "YYYY-MM-DD" format.
     */
    @ColumnInfo(name = "effective_from")
    val effectiveFrom: String,

    /**
     * Inclusive end civil date in ISO-8601 "YYYY-MM-DD" format. Null indicates the current active version.
     */
    @ColumnInfo(name = "effective_to", defaultValue = "NULL")
    val effectiveTo: String? = null
)
