package com.habit1.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyGoalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: DailyGoalEntity)

    @Update
    suspend fun updateGoal(goal: DailyGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: DailyGoalEntity)

    @Query("DELETE FROM daily_goals WHERE id = :id")
    suspend fun deleteGoalById(id: String)

    @Query("SELECT * FROM daily_goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: String): DailyGoalEntity?

    @Transaction
    @Query("SELECT * FROM daily_goals WHERE id = :id LIMIT 1")
    suspend fun getGoalWithSubtasksById(id: String): DailyGoalWithSubtasks?

    @Transaction
    @Query("SELECT * FROM daily_goals WHERE target_date = :date ORDER BY display_order ASC, created_at ASC")
    fun observeGoalsForDate(date: String): Flow<List<DailyGoalWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM daily_goals WHERE target_date = :date ORDER BY display_order ASC, created_at ASC")
    suspend fun getGoalsForDate(date: String): List<DailyGoalWithSubtasks>

    @Transaction
    @Query("SELECT * FROM daily_goals WHERE target_date BETWEEN :startDate AND :endDate ORDER BY target_date ASC, display_order ASC")
    fun observeGoalsForDateRange(startDate: String, endDate: String): Flow<List<DailyGoalWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM daily_goals WHERE target_date BETWEEN :startDate AND :endDate ORDER BY target_date ASC, display_order ASC")
    suspend fun getGoalsForDateRange(startDate: String, endDate: String): List<DailyGoalWithSubtasks>

    @Update
    suspend fun updateAllGoals(goals: List<DailyGoalEntity>)

    @Query("UPDATE daily_goals SET target_date = :newDate, display_order = :newOrder, is_completed = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun moveGoalDate(id: String, newDate: String, newOrder: Int, updatedAt: Long)

    @Query("UPDATE daily_goals SET title = :title, notes = :notes, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateGoalContent(id: String, title: String, notes: String?, updatedAt: Long)

    @Query("UPDATE daily_goals SET is_completed = :isCompleted, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateGoalCompletion(id: String, isCompleted: Boolean, updatedAt: Long)

    @Query("UPDATE daily_goals SET display_order = :displayOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateGoalOrder(id: String, displayOrder: Int, updatedAt: Long)

    // Subtasks
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubtask(subtask: GoalSubtaskEntity)

    @Update
    suspend fun updateSubtask(subtask: GoalSubtaskEntity)

    @Update
    suspend fun updateAllSubtasks(subtasks: List<GoalSubtaskEntity>)

    @Delete
    suspend fun deleteSubtask(subtask: GoalSubtaskEntity)

    @Query("DELETE FROM goal_subtasks WHERE id = :id")
    suspend fun deleteSubtaskById(id: String)

    @Query("UPDATE goal_subtasks SET title = :title WHERE id = :id")
    suspend fun updateSubtaskTitle(id: String, title: String)

    @Query("UPDATE goal_subtasks SET is_completed = :isCompleted WHERE id = :id")
    suspend fun updateSubtaskCompletion(id: String, isCompleted: Boolean)

    @Query("SELECT * FROM goal_subtasks WHERE goal_id = :goalId ORDER BY display_order ASC, created_at ASC")
    suspend fun getSubtasksForGoal(goalId: String): List<GoalSubtaskEntity>

    @Query("DELETE FROM goal_subtasks WHERE goal_id = :goalId")
    suspend fun deleteSubtasksForGoal(goalId: String)

    @Query("SELECT COUNT(*) FROM daily_goals WHERE target_date = :date AND is_completed = 1")
    suspend fun countCompletedGoalsOnDate(date: String): Int

    @Query("SELECT COUNT(*) FROM daily_goals WHERE target_date = :date")
    suspend fun countTotalGoalsOnDate(date: String): Int

    @Transaction
    @Query("SELECT * FROM daily_goals ORDER BY target_date ASC, display_order ASC")
    suspend fun getAllGoalsWithSubtasks(): List<DailyGoalWithSubtasks>

    @Query("SELECT * FROM daily_goals ORDER BY target_date ASC, display_order ASC")
    suspend fun getAllGoalsList(): List<DailyGoalEntity>

    @Query("SELECT * FROM goal_subtasks ORDER BY goal_id ASC, display_order ASC")
    suspend fun getAllSubtasksList(): List<GoalSubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllGoals(goals: List<DailyGoalEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllSubtasks(subtasks: List<GoalSubtaskEntity>)

    @Query("DELETE FROM goal_subtasks")
    suspend fun deleteAllSubtasks()

    @Query("DELETE FROM daily_goals")
    suspend fun deleteAllGoals()
}
