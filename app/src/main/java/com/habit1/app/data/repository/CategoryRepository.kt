package com.habit1.app.data.repository

import com.habit1.app.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user-defined habit categories.
 */
interface CategoryRepository {
    fun observeAllCategories(): Flow<List<CategoryEntity>>
    suspend fun getAllCategories(): List<CategoryEntity>
    suspend fun getCategoryById(id: String): CategoryEntity?
    suspend fun createCategory(category: CategoryEntity)
    suspend fun updateCategory(category: CategoryEntity)
    suspend fun deleteCategory(id: String)
    suspend fun reorderCategories(orderedIds: List<String>)
}
