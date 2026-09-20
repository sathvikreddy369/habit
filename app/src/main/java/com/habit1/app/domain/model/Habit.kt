package com.habit1.app.domain.model

import java.time.Instant
import java.time.LocalTime

/**
 * Pure domain representation of a tracked habit, completely decoupled from Room annotations.
 */
data class Habit(
    val id: String,
    val name: String,
    val description: String? = null,
    val measurement: MeasurementType,
    val schedule: HabitSchedule,
    val reminderTime: LocalTime? = null,
    val displayOrder: Int = 0,
    val isPaused: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)
