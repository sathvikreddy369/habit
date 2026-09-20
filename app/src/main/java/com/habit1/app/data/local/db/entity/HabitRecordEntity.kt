package com.habit1.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historical record of a habit on a specific calendar date.
 *
 * Preserves Historical Truth (Product Principle #7):
 * - Missed days are represented by the absence of a completion record; records are NEVER fabricated.
 * - Snapshots measurement_type, target_value, and unit at the moment of recording, ensuring that
 *   future edits to a habit's definition never retroactively reinterpret historical accomplishments.
 */
@Entity(
    tableName = "habit_records",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habit_id", "date"], unique = true),
        Index(value = ["date"]),
        Index(value = ["habit_id", "is_completed"])
    ]
)
data class HabitRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "habit_id")
    val habitId: String,

    /**
     * Local calendar date in ISO-8601 "YYYY-MM-DD" format.
     * Stored as a string to guarantee stable lexicographical sorting and immunity to timezone shifts.
     */
    @ColumnInfo(name = "date")
    val date: String,

    /**
     * Actual measured value (e.g. 1.0 for boolean completion, 45 for count, 30.0 for minutes).
     */
    @ColumnInfo(name = "actual_value")
    val actualValue: Double,

    /**
     * Historical snapshot of target value at the time this record was saved.
     */
    @ColumnInfo(name = "target_value")
    val targetValue: Double,

    /**
     * Historical snapshot of measurement type at the time this record was saved.
     */
    @ColumnInfo(name = "measurement_type")
    val measurementType: String,

    /**
     * Historical snapshot of unit at the time this record was saved.
     */
    @ColumnInfo(name = "unit")
    val unit: String? = null,

    /**
     * Authoritative completion status for this calendar day.
     */
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long
)
