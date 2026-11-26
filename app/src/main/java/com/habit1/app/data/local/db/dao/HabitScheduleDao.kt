package com.habit1.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habit1.app.data.local.db.entity.HabitScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitScheduleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(schedule: HabitScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<HabitScheduleEntity>)

    @Update
    suspend fun update(schedule: HabitScheduleEntity)

    @Delete
    suspend fun delete(schedule: HabitScheduleEntity)

    @Query("DELETE FROM habit_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM habit_schedules WHERE habit_id = :habitId ORDER BY effective_from ASC")
    suspend fun getSchedulesForHabit(habitId: String): List<HabitScheduleEntity>

    @Query("SELECT * FROM habit_schedules WHERE habit_id = :habitId ORDER BY effective_from ASC")
    fun observeSchedulesForHabit(habitId: String): Flow<List<HabitScheduleEntity>>

    @Query("SELECT * FROM habit_schedules ORDER BY effective_from ASC")
    suspend fun getAllSchedules(): List<HabitScheduleEntity>

    @Query("SELECT * FROM habit_schedules ORDER BY effective_from ASC")
    fun observeAllSchedules(): Flow<List<HabitScheduleEntity>>

    /**
     * Gets the authoritative schedule version active on a specific civil date.
     */
    @Query("""
        SELECT * FROM habit_schedules 
        WHERE habit_id = :habitId 
          AND effective_from <= :date 
          AND (effective_to IS NULL OR effective_to >= :date)
        ORDER BY effective_from DESC
        LIMIT 1
    """)
    suspend fun getScheduleOnDate(habitId: String, date: String): HabitScheduleEntity?

    @Query("DELETE FROM habit_schedules WHERE habit_id = :habitId")
    suspend fun deleteSchedulesForHabit(habitId: String)

    @Query("DELETE FROM habit_schedules")
    suspend fun deleteAllSchedules()
}
