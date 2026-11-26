package com.habit1.app.data.repository

import com.habit1.app.data.local.db.dao.CategoryDao
import com.habit1.app.data.local.db.dao.HabitDao
import com.habit1.app.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Production implementation of [CategoryRepository] backed by Room SQLite DAOs.
 *
 * Preserves Historical Truth & Invariant Safety (Decision 6.1):
 * - Deleting a category sets habits' category_id to NULL.
 * - Habits and historical habit records are never deleted upon category removal.
 */
class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val habitDao: HabitDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CategoryRepository {

    override fun observeAllCategories(): Flow<List<CategoryEntity>> =
        categoryDao.observeAllCategories().flowOn(ioDispatcher)

    override suspend fun getAllCategories(): List<CategoryEntity> =
        withContext(ioDispatcher) {
            categoryDao.getAllCategories()
        }

    override suspend fun getCategoryById(id: String): CategoryEntity? =
        withContext(ioDispatcher) {
            categoryDao.getById(id)
        }

    override suspend fun createCategory(category: CategoryEntity) =
        withContext(ioDispatcher) {
            categoryDao.insert(category)
        }

    override suspend fun updateCategory(category: CategoryEntity) =
        withContext(ioDispatcher) {
            categoryDao.update(category)
        }

    override suspend fun deleteCategory(id: String) =
        withContext(ioDispatcher) {
            // Unlink all habits before category deletion to ensure zero habit data loss
            habitDao.clearCategoryId(id)
            categoryDao.deleteById(id)
        }

    override suspend fun reorderCategories(orderedIds: List<String>) =
        withContext(ioDispatcher) {
            orderedIds.forEachIndexed { index, id ->
                categoryDao.updateDisplayOrder(id, index)
            }
        }
}
