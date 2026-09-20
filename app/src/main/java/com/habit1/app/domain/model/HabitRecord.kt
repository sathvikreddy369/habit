package com.habit1.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Pure domain representation of a recorded habit accomplishment on a civil calendar date.
 *
 * Preserves Historical Truth:
 * - date: The civil calendar day to which this record belongs.
 * - recordedAt: Exact UTC instant when the record was saved or edited.
 * - targetValue, measurementType, unit: Snapshot of the habit's parameters when recorded.
 *   Never retroactively changed when the parent Habit definition is updated.
 */
data class HabitRecord(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val actualValue: Double,
    val targetValue: Double,
    val measurementType: String,
    val unit: String? = null,
    val isCompleted: Boolean,
    val notes: String? = null,
    val recordedAt: Instant
)
