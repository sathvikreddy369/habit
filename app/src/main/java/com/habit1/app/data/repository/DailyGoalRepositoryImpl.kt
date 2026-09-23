package com.habit1.app.data.repository

import com.habit1.app.data.local.db.dao.DailyGoalDao
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DailyGoalRepositoryImpl(
    private val dailyGoalDao: DailyGoalDao,
    private val ioDispatcher: CoroutineDispatcher
) : DailyGoalRepository {

    override fun observeGoalsForDate(date: String): Flow<List<DailyGoalWithSubtasks>> =
        dailyGoalDao.observeGoalsForDate(date).flowOn(ioDispatcher)

    override fun observeGoalsForDateRange(startDate: String, endDate: String): Flow<List<DailyGoalWithSubtasks>> =
        dailyGoalDao.observeGoalsForDateRange(startDate, endDate).flowOn(ioDispatcher)

    override suspend fun getGoalsForDate(date: String): List<DailyGoalWithSubtasks> =
        withContext(ioDispatcher) {
            dailyGoalDao.getGoalsForDate(date)
        }

    override suspend fun getGoalsForDateRange(startDate: String, endDate: String): List<DailyGoalWithSubtasks> =
        withContext(ioDispatcher) {
            dailyGoalDao.getGoalsForDateRange(startDate, endDate)
        }

    override suspend fun getGoalById(id: String): DailyGoalWithSubtasks? =
        withContext(ioDispatcher) {
            dailyGoalDao.getGoalWithSubtasksById(id)
        }

    override suspend fun createGoal(goal: DailyGoalEntity) =
        withContext(ioDispatcher) {
            dailyGoalDao.insertGoal(goal)
        }

    override suspend fun updateGoal(goal: DailyGoalEntity) =
        withContext(ioDispatcher) {
            dailyGoalDao.updateGoal(goal)
        }

    override suspend fun updateGoalContent(id: String, title: String, notes: String?) =
        withContext(ioDispatcher) {
            dailyGoalDao.updateGoalContent(id, title.trim(), notes?.trim(), System.currentTimeMillis())
        }

    override suspend fun deleteGoal(id: String) =
        withContext(ioDispatcher) {
            dailyGoalDao.deleteGoalById(id)
        }

    override suspend fun setGoalCompleted(id: String, isCompleted: Boolean) =
        withContext(ioDispatcher) {
            dailyGoalDao.updateGoalCompletion(id, isCompleted, System.currentTimeMillis())
        }

    override suspend fun moveGoalDate(id: String, newDate: String, newOrder: Int) =
        withContext(ioDispatcher) {
            dailyGoalDao.moveGoalDate(id, newDate, newOrder, System.currentTimeMillis())
        }

    override suspend fun reorderGoals(date: String, orderedIds: List<String>) =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val currentGoals = dailyGoalDao.getGoalsForDate(date).associateBy { it.goal.id }
            val updated = orderedIds.mapIndexedNotNull { index, id ->
                currentGoals[id]?.goal?.copy(displayOrder = index, updatedAt = now)
            }
            if (updated.isNotEmpty()) {
                dailyGoalDao.updateAllGoals(updated)
            }
        }

    override suspend fun addSubtask(subtask: GoalSubtaskEntity) =
        withContext(ioDispatcher) {
            dailyGoalDao.insertSubtask(subtask)
        }

    override suspend fun updateSubtask(subtask: GoalSubtaskEntity) =
        withContext(ioDispatcher) {
            dailyGoalDao.updateSubtask(subtask)
        }

    override suspend fun updateSubtaskTitle(id: String, title: String) =
        withContext(ioDispatcher) {
            dailyGoalDao.updateSubtaskTitle(id, title.trim())
        }

    override suspend fun deleteSubtask(id: String) =
        withContext(ioDispatcher) {
            dailyGoalDao.deleteSubtaskById(id)
        }

    override suspend fun setSubtaskCompleted(id: String, isCompleted: Boolean) =
        withContext(ioDispatcher) {
            dailyGoalDao.updateSubtaskCompletion(id, isCompleted)
        }

    override suspend fun reorderSubtasks(goalId: String, orderedIds: List<String>) =
        withContext(ioDispatcher) {
            val currentSubtasks = dailyGoalDao.getSubtasksForGoal(goalId).associateBy { it.id }
            val updated = orderedIds.mapIndexedNotNull { index, id ->
                currentSubtasks[id]?.copy(displayOrder = index)
            }
            if (updated.isNotEmpty()) {
                dailyGoalDao.updateAllSubtasks(updated)
            }
        }

    override suspend fun cleanupCompletedGoalsBeforeDate(beforeDate: String): Int =
        withContext(ioDispatcher) {
            dailyGoalDao.cleanupCompletedGoalsBeforeDate(beforeDate)
        }

    override suspend fun cleanupIncompleteGoalsOlderThan(cutoffDate: String): Int =
        withContext(ioDispatcher) {
            dailyGoalDao.deleteIncompleteGoalsOlderThan(cutoffDate)
        }

    override fun observeAggregatesForDateRange(
        startDate: String,
        endDate: String
    ): Flow<List<com.habit1.app.domain.model.DailyGoalHistoryAggregate>> {
        return dailyGoalDao.observeAggregatesForDateRange(startDate, endDate)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAggregatesForDateRange(
        startDate: String,
        endDate: String
    ): List<com.habit1.app.domain.model.DailyGoalHistoryAggregate> =
        withContext(ioDispatcher) {
            dailyGoalDao.getAggregatesForDateRange(startDate, endDate).map { it.toDomain() }
        }

    override suspend fun getAggregateForDate(date: String): com.habit1.app.domain.model.DailyGoalHistoryAggregate? =
        withContext(ioDispatcher) {
            dailyGoalDao.getAggregateForDate(date)?.toDomain()
        }
}

