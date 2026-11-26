package com.habit1.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habit1.app.data.local.db.entity.HabitPauseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitPauseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pause: HabitPauseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pauses: List<HabitPauseEntity>)

    @Update
    suspend fun update(pause: HabitPauseEntity)

    @Delete
    suspend fun delete(pause: HabitPauseEntity)

    @Query("DELETE FROM habit_pauses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM habit_pauses WHERE habit_id = :habitId ORDER BY start_date ASC")
    suspend fun getPausesForHabit(habitId: String): List<HabitPauseEntity>

    @Query("SELECT * FROM habit_pauses WHERE habit_id = :habitId ORDER BY start_date ASC")
    fun observePausesForHabit(habitId: String): Flow<List<HabitPauseEntity>>

    @Query("SELECT * FROM habit_pauses ORDER BY start_date ASC")
    suspend fun getAllPauses(): List<HabitPauseEntity>

    @Query("SELECT * FROM habit_pauses ORDER BY start_date ASC")
    fun observeAllPauses(): Flow<List<HabitPauseEntity>>

    /**
     * Checks if a habit is paused on a specific date (start_date <= date AND (end_date IS NULL OR end_date >= date)).
     */
    @Query("""
        SELECT * FROM habit_pauses 
        WHERE habit_id = :habitId 
          AND start_date <= :date 
          AND (end_date IS NULL OR end_date >= :date)
        LIMIT 1
    """)
    suspend fun getActivePauseOnDate(habitId: String, date: String): HabitPauseEntity?

    @Query("DELETE FROM habit_pauses WHERE habit_id = :habitId")
    suspend fun deletePausesForHabit(habitId: String)

    @Query("DELETE FROM habit_pauses")
    suspend fun deleteAllPauses()
}
