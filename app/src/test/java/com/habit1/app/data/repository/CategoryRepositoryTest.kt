package com.habit1.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.CategoryEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        categoryRepository = CategoryRepositoryImpl(
            categoryDao = database.categoryDao(),
            habitDao = database.habitDao(),
            ioDispatcher = testDispatcher
        )
        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        habitRecordRepository = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun freshInstall_startsEmptyWithNoPredefinedCategories() = runTest(testDispatcher) {
        val categories = categoryRepository.getAllCategories()
        assertTrue("Fresh install must not have predefined categories (Decision 6.1)", categories.isEmpty())
    }

    @Test
    fun createAndRetrieveCategory() = runTest(testDispatcher) {
        val cat = CategoryEntity(
            id = "cat_health",
            name = "Health",
            colorHex = "#EC407A",
            icon = "health",
            displayOrder = 0,
            createdAt = 1000L
        )
        categoryRepository.createCategory(cat)

        val retrieved = categoryRepository.getCategoryById("cat_health")
        assertNotNull(retrieved)
        assertEquals("Health", retrieved?.name)
        assertEquals("#EC407A", retrieved?.colorHex)
        assertEquals("health", retrieved?.icon)
    }

    @Test
    fun updateCategory_modifiesFieldsCorrectly() = runTest(testDispatcher) {
        val cat = CategoryEntity(
            id = "cat_work",
            name = "Work",
            colorHex = "#42A5F5",
            icon = "work",
            displayOrder = 0,
            createdAt = 1000L
        )
        categoryRepository.createCategory(cat)

        categoryRepository.updateCategory(
            cat.copy(name = "Productivity", colorHex = "#7E57C2", icon = "target")
        )

        val updated = categoryRepository.getCategoryById("cat_work")
        assertEquals("Productivity", updated?.name)
        assertEquals("#7E57C2", updated?.colorHex)
        assertEquals("target", updated?.icon)
    }

    @Test
    fun deleteCategory_unlinksHabits_neverDeletesHabitsOrRecords() = runTest(testDispatcher) {
        // 1. Create Category
        val cat = CategoryEntity(
            id = "cat_fitness",
            name = "Fitness",
            colorHex = "#F08A6C",
            icon = "fitness",
            displayOrder = 0,
            createdAt = 1000L
        )
        categoryRepository.createCategory(cat)

        // 2. Create Habit assigned to that category
        val habit = HabitEntity(
            id = "h_running",
            name = "Running",
            measurementType = "BOOLEAN",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            categoryId = "cat_fitness",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        habitRepository.createHabit(habit)

        // 3. Create a historical record for that habit
        val record = HabitRecordEntity(
            id = "r_1",
            habitId = "h_running",
            date = "2026-03-01",
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = true,
            status = "DONE",
            recordedAt = 1000L
        )
        habitRecordRepository.recordProgress(record)

        // Verify initial links
        assertEquals("cat_fitness", habitRepository.getHabitById("h_running")?.categoryId)

        // 4. Delete the category
        categoryRepository.deleteCategory("cat_fitness")

        // 5. Invariant Checks: Category is deleted
        assertNull(categoryRepository.getCategoryById("cat_fitness"))

        // 6. Invariant Checks: Habit is NOT deleted, its categoryId is set to NULL
        val habitAfter = habitRepository.getHabitById("h_running")
        assertNotNull("Habit must not be deleted on category removal (Decision 6.1)", habitAfter)
        assertNull("Habit categoryId must revert to null upon category removal", habitAfter?.categoryId)

        // 7. Invariant Checks: Historical record is completely intact
        val recordAfter = habitRecordRepository.getRecord("h_running", "2026-03-01")
        assertNotNull("Historical records must never be lost on category deletion", recordAfter)
        assertEquals("DONE", recordAfter?.status)
    }

    @Test
    fun reorderCategories_updatesDisplayOrder() = runTest(testDispatcher) {
        val c1 = CategoryEntity(id = "c1", name = "First", displayOrder = 0, createdAt = 1000L)
        val c2 = CategoryEntity(id = "c2", name = "Second", displayOrder = 1, createdAt = 1000L)
        categoryRepository.createCategory(c1)
        categoryRepository.createCategory(c2)

        categoryRepository.reorderCategories(listOf("c2", "c1"))

        val all = categoryRepository.observeAllCategories().first()
        assertEquals("c2", all[0].id)
        assertEquals(0, all[0].displayOrder)
        assertEquals("c1", all[1].id)
        assertEquals(1, all[1].displayOrder)
    }
}
