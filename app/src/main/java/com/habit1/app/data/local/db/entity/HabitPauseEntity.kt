package com.habit1.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Authoritative temporal pause interval for a habit.
 *
 * Replaces the legacy scalar is_paused boolean to preserve historical truth (ADR-08 & Product Principle #7):
 * - Pause intervals preserve historical scheduling, streaks, and completion rates.
 * - Current pause state never retroactively alters dates outside the recorded pause interval.
 * - start_date and end_date are inclusive local civil dates in ISO-8601 "YYYY-MM-DD".
 * - A habit resumes the day after its inclusive end_date (or remains paused if end_date is null).
 */
@Entity(
    tableName = "habit_pauses",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habit_id", "start_date"])
    ]
)
data class HabitPauseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "habit_id")
    val habitId: String,

    /**
     * Start of the pause interval (inclusive) in ISO-8601 "YYYY-MM-DD" format.
     */
    @ColumnInfo(name = "start_date")
    val startDate: String,

    /**
     * End of the pause interval (inclusive) in ISO-8601 "YYYY-MM-DD" format.
     * Null indicates an ongoing/indefinite pause.
     */
    @ColumnInfo(name = "end_date", defaultValue = "NULL")
    val endDate: String? = null,

    @ColumnInfo(name = "reason", defaultValue = "NULL")
    val reason: String? = null
)
