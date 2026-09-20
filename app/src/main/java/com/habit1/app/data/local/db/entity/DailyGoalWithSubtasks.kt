package com.habit1.app.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Composite relationship representing a DailyGoal and all its associated Subtasks.
 */
data class DailyGoalWithSubtasks(
    @Embedded
    val goal: DailyGoalEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "goal_id"
    )
    val subtasks: List<GoalSubtaskEntity> = emptyList()
)
