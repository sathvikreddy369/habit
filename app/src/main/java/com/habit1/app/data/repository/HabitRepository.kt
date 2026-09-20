package com.habit1.app.data.repository

import com.habit1.app.data.local.db.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    fun observeActiveHabits(): Flow<List<HabitEntity>>
    fun observeAllHabits(): Flow<List<HabitEntity>>
    fun observeHabitById(id: String): Flow<HabitEntity?>
    suspend fun getHabitById(id: String): HabitEntity?
    suspend fun getActiveHabitsList(): List<HabitEntity>
    suspend fun createHabit(habit: HabitEntity)
    suspend fun updateHabit(habit: HabitEntity)
    suspend fun deleteHabit(id: String)
    suspend fun archiveHabit(id: String, isArchived: Boolean)
    suspend fun pauseHabit(id: String, isPaused: Boolean)
    suspend fun reorderHabits(orderedIds: List<String>)
}
