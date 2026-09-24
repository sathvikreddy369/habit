package com.habit1.app.data.repository

import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import kotlinx.coroutines.flow.Flow

interface DailyGoalRepository {
    fun observeGoalsForDate(date: String): Flow<List<DailyGoalWithSubtasks>>
    fun observeGoalsForDateRange(startDate: String, endDate: String): Flow<List<DailyGoalWithSubtasks>>
    suspend fun getGoalsForDate(date: String): List<DailyGoalWithSubtasks>
    suspend fun getGoalsForDateRange(startDate: String, endDate: String): List<DailyGoalWithSubtasks>
    suspend fun getGoalById(id: String): DailyGoalWithSubtasks?
    suspend fun createGoal(goal: DailyGoalEntity)
    suspend fun updateGoal(goal: DailyGoalEntity)
    suspend fun updateGoalContent(id: String, title: String, notes: String?)
    suspend fun updateGoalContentWithReminder(id: String, title: String, notes: String?, reminderTime: String?)
    suspend fun updateGoalReminder(id: String, reminderTime: String?)
    suspend fun deleteGoal(id: String)
    suspend fun setGoalCompleted(id: String, isCompleted: Boolean)
    suspend fun moveGoalDate(id: String, newDate: String, newOrder: Int)
    suspend fun reorderGoals(date: String, orderedIds: List<String>)

    // Subtasks
    suspend fun addSubtask(subtask: GoalSubtaskEntity)
    suspend fun updateSubtask(subtask: GoalSubtaskEntity)
    suspend fun updateSubtaskTitle(id: String, title: String)
    suspend fun deleteSubtask(id: String)
    suspend fun setSubtaskCompleted(id: String, isCompleted: Boolean)
    suspend fun reorderSubtasks(goalId: String, orderedIds: List<String>)

    /**
     * Cleans up ephemeral past completed goals whose target date is before [beforeDate].
     * Associated subtasks are cleanly removed.
     * Historical completion aggregates (date, completedCount, totalCount) are atomically persisted.
     * Past incomplete goals, current goals, and future goals are preserved.
     */
    suspend fun cleanupCompletedGoalsBeforeDate(beforeDate: String): Int

    /**
     * Purges incomplete daily goals older than 7 calendar days before today (target_date <= cutoffDate).
     * Completed goals are NEVER removed (permanent personal history).
     * Subtasks of deleted goals cascade delete automatically via Room foreign key.
     */
    suspend fun cleanupIncompleteGoalsOlderThan(cutoffDate: String): Int

    // Historical Aggregates
    fun observeAggregatesForDateRange(startDate: String, endDate: String): Flow<List<com.habit1.app.domain.model.DailyGoalHistoryAggregate>>
    suspend fun getAggregatesForDateRange(startDate: String, endDate: String): List<com.habit1.app.domain.model.DailyGoalHistoryAggregate>
    suspend fun getAggregateForDate(date: String): com.habit1.app.domain.model.DailyGoalHistoryAggregate?
}
