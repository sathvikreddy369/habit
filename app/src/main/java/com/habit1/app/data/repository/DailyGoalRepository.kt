package com.habit1.app.data.repository

import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import kotlinx.coroutines.flow.Flow

interface DailyGoalRepository {
    fun observeGoalsForDate(date: String): Flow<List<DailyGoalWithSubtasks>>
    suspend fun getGoalsForDate(date: String): List<DailyGoalWithSubtasks>
    suspend fun getGoalsForDateRange(startDate: String, endDate: String): List<DailyGoalWithSubtasks>
    suspend fun getGoalById(id: String): DailyGoalWithSubtasks?
    suspend fun createGoal(goal: DailyGoalEntity)
    suspend fun updateGoal(goal: DailyGoalEntity)
    suspend fun deleteGoal(id: String)
    suspend fun setGoalCompleted(id: String, isCompleted: Boolean)
    suspend fun moveGoalDate(id: String, newDate: String, newOrder: Int)
    suspend fun reorderGoals(date: String, orderedIds: List<String>)

    // Subtasks
    suspend fun addSubtask(subtask: GoalSubtaskEntity)
    suspend fun updateSubtask(subtask: GoalSubtaskEntity)
    suspend fun deleteSubtask(id: String)
    suspend fun setSubtaskCompleted(id: String, isCompleted: Boolean)
}
