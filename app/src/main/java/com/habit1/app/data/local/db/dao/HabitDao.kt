package com.habit1.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.habit1.app.data.local.db.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(habit: HabitEntity)

    @Update
    suspend fun update(habit: HabitEntity)

    @Update
    suspend fun updateAll(habits: List<HabitEntity>)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE is_archived = 0 ORDER BY display_order ASC, created_at ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE is_archived = 0 ORDER BY display_order ASC, created_at ASC")
    suspend fun getActiveHabitsList(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE is_archived = 1 ORDER BY display_order ASC, created_at ASC")
    fun observeArchivedHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY is_archived ASC, display_order ASC, created_at ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY is_archived ASC, display_order ASC, created_at ASC")
    suspend fun getAllHabitsList(): List<HabitEntity>

    @Query("UPDATE habits SET display_order = :displayOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateDisplayOrder(id: String, displayOrder: Int, updatedAt: Long)

    @Query("UPDATE habits SET is_archived = :isArchived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean, updatedAt: Long)

    @Query("UPDATE habits SET is_paused = :isPaused, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPaused(id: String, isPaused: Boolean, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM habits WHERE is_archived = 0")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(habits: List<HabitEntity>)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()
}
