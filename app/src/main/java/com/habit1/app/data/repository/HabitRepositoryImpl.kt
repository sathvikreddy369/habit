package com.habit1.app.data.repository

import com.habit1.app.data.local.db.dao.HabitDao
import com.habit1.app.data.local.db.entity.HabitEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class HabitRepositoryImpl(
    private val habitDao: HabitDao,
    private val ioDispatcher: CoroutineDispatcher
) : HabitRepository {

    override fun observeActiveHabits(): Flow<List<HabitEntity>> =
        habitDao.observeActiveHabits().flowOn(ioDispatcher)

    override fun observeArchivedHabits(): Flow<List<HabitEntity>> =
        habitDao.observeArchivedHabits().flowOn(ioDispatcher)

    override fun observeAllHabits(): Flow<List<HabitEntity>> =
        habitDao.observeAllHabits().flowOn(ioDispatcher)

    override fun observeHabitById(id: String): Flow<HabitEntity?> =
        habitDao.observeById(id).flowOn(ioDispatcher)

    override suspend fun getHabitById(id: String): HabitEntity? =
        withContext(ioDispatcher) {
            habitDao.getById(id)
        }

    override suspend fun getActiveHabitsList(): List<HabitEntity> =
        withContext(ioDispatcher) {
            habitDao.getActiveHabitsList()
        }

    override suspend fun createHabit(habit: HabitEntity) =
        withContext(ioDispatcher) {
            habitDao.insert(habit)
        }

    override suspend fun updateHabit(habit: HabitEntity) =
        withContext(ioDispatcher) {
            habitDao.update(habit)
        }

    override suspend fun deleteHabit(id: String) =
        withContext(ioDispatcher) {
            habitDao.deleteById(id)
        }

    override suspend fun archiveHabit(id: String, isArchived: Boolean) =
        withContext(ioDispatcher) {
            habitDao.setArchived(id, isArchived, System.currentTimeMillis())
        }

    override suspend fun pauseHabit(id: String, isPaused: Boolean) =
        withContext(ioDispatcher) {
            habitDao.setPaused(id, isPaused, System.currentTimeMillis())
        }

    override suspend fun reorderHabits(orderedIds: List<String>) =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val allHabits = habitDao.getActiveHabitsList().associateBy { it.id }
            val updated = orderedIds.mapIndexedNotNull { index, id ->
                allHabits[id]?.copy(displayOrder = index, updatedAt = now)
            }
            if (updated.isNotEmpty()) {
                habitDao.updateAll(updated)
            }
        }
}
