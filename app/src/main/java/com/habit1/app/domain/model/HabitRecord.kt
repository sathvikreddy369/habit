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
 * - status: Authoritative 4-state status (DONE, PARTIAL, SKIPPED, MISSED).
 */
data class HabitRecord(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val actualValue: Double,
    val targetValue: Double,
    val measurementType: String,
    val unit: String? = null,
    val isCompleted: Boolean = true,
    val notes: String? = null,
    val recordedAt: Instant,
    val status: HabitRecordStatus = if (isCompleted) HabitRecordStatus.DONE else HabitRecordStatus.PARTIAL
) {
    constructor(
        id: String,
        habitId: String,
        date: LocalDate,
        actualValue: Double,
        targetValue: Double,
        measurementType: String,
        unit: String? = null,
        status: HabitRecordStatus,
        notes: String? = null,
        recordedAt: Instant
    ) : this(
        id = id,
        habitId = habitId,
        date = date,
        actualValue = actualValue,
        targetValue = targetValue,
        measurementType = measurementType,
        unit = unit,
        isCompleted = (status == HabitRecordStatus.DONE),
        notes = notes,
        recordedAt = recordedAt,
        status = status
    )
}
