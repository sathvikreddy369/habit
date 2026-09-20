package com.habit1.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * An individual subtask under a DailyGoal.
 */
data class GoalSubtask(
    val id: String,
    val goalId: String,
    val title: String,
    val isCompleted: Boolean = false,
    val displayOrder: Int = 0,
    val createdAt: Instant
)

/**
 * Pure domain representation of a daily outcome targeted for a specific civil date.
 */
data class DailyGoal(
    val id: String,
    val title: String,
    val targetDate: LocalDate,
    val isCompleted: Boolean = false,
    val displayOrder: Int = 0,
    val subtasks: List<GoalSubtask> = emptyList(),
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
