package com.habit1.app.ui.habits.list

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class HabitListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var categoryRepository: com.habit1.app.data.repository.CategoryRepository
    private lateinit var habitRecordRepository: com.habit1.app.data.repository.HabitRecordRepository
    private lateinit var viewModel: HabitListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        categoryRepository = com.habit1.app.data.repository.CategoryRepositoryImpl(
            categoryDao = database.categoryDao(),
            habitDao = database.habitDao(),
            ioDispatcher = testDispatcher
        )
        habitRecordRepository = com.habit1.app.data.repository.HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
        viewModel = HabitListViewModel(
            habitRepository = habitRepository,
            categoryRepository = categoryRepository,
            habitRecordRepository = habitRecordRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun pauseAndResume_independentOfArchival() = runTest(testDispatcher) {
        val habit = HabitEntity(
            id = "h1",
            name = "Yoga",
            measurementType = "BOOLEAN",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        habitRepository.createHabit(habit)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // initial
            val state = awaitItem()
            assertFalse(state.activeHabits[0].isPaused)
            assertFalse(state.activeHabits[0].isArchived)

            // Pause
            viewModel.onEvent(HabitListUiEvent.PauseHabit("h1"))
            val pausedState = awaitItem()
            assertTrue(pausedState.activeHabits[0].isPaused)
            assertFalse(pausedState.activeHabits[0].isArchived)

            // Resume
            viewModel.onEvent(HabitListUiEvent.ResumeHabit("h1"))
            val resumedState = awaitItem()
            assertFalse(resumedState.activeHabits[0].isPaused)
            assertFalse(resumedState.activeHabits[0].isArchived)
        }
    }

    @Test
    fun archiveAndUnarchive_preservesPausedStateOnUnarchive() = runTest(testDispatcher) {
        // Create habit that is paused
        val habit = HabitEntity(
            id = "h2",
            name = "Piano",
            measurementType = "BOOLEAN",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = true, // Paused before archival!
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        habitRepository.createHabit(habit)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            val state1 = awaitItem()
            assertEquals(1, state1.activeHabits.size)
            assertEquals(0, state1.archivedHabits.size)

            // Archive habit
            viewModel.onEvent(HabitListUiEvent.ArchiveHabit("h2"))
            val archivedState = awaitItem()
            assertEquals(0, archivedState.activeHabits.size)
            assertEquals(1, archivedState.archivedHabits.size)
            assertTrue(archivedState.archivedHabits[0].isArchived)
            assertTrue(archivedState.archivedHabits[0].isPaused) // Paused state maintained

            // Unarchive habit
            viewModel.onEvent(HabitListUiEvent.UnarchiveHabit("h2"))
            val unarchivedState = awaitItem()
            assertEquals(1, unarchivedState.activeHabits.size)
            assertEquals(0, unarchivedState.archivedHabits.size)
            assertFalse(unarchivedState.activeHabits[0].isArchived)
            // USER RULE: Unarchiving leaves habit paused until explicitly resumed!
            assertTrue(unarchivedState.activeHabits[0].isPaused)
        }
    }

    @Test
    fun reorderHabits_moveUpAndMoveDown() = runTest(testDispatcher) {
        val h1 = HabitEntity(id = "h1", name = "First", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 0, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        val h2 = HabitEntity(id = "h2", name = "Second", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 1, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        habitRepository.createHabit(h1)
        habitRepository.createHabit(h2)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertEquals("First", state.activeHabits[0].name)
            assertEquals("Second", state.activeHabits[1].name)

            // Move h2 up
            viewModel.onEvent(HabitListUiEvent.MoveUp("h2"))
            val reordered = awaitItem()
            assertEquals("Second", reordered.activeHabits[0].name)
            assertEquals("First", reordered.activeHabits[1].name)

            // Move h2 down
            viewModel.onEvent(HabitListUiEvent.MoveDown("h2"))
            val back = awaitItem()
            assertEquals("First", back.activeHabits[0].name)
            assertEquals("Second", back.activeHabits[1].name)
        }
    }

    @Test
    fun deleteHabit_requiresConfirmationAndRemovesHabit() = runTest(testDispatcher) {
        val habit = HabitEntity(id = "h_del", name = "Temp Habit", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 0, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        habitRepository.createHabit(habit)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            val item = state.activeHabits[0]

            // Request delete
            viewModel.onEvent(HabitListUiEvent.RequestDeleteHabit(item))
            val pendingState = awaitItem()
            assertEquals("Temp Habit", pendingState.habitPendingDeletion?.name)

            // Cancel delete
            viewModel.onEvent(HabitListUiEvent.CancelDeleteHabit)
            val cancelledState = awaitItem()
            assertNull(cancelledState.habitPendingDeletion)
            assertNotNull(habitRepository.getHabitById("h_del"))

            // Request delete and confirm
            viewModel.onEvent(HabitListUiEvent.RequestDeleteHabit(item))
            awaitItem()
            viewModel.onEvent(HabitListUiEvent.ConfirmDeleteHabit)
            var deletedState = awaitItem()
            if (deletedState.activeHabits.isNotEmpty()) {
                deletedState = awaitItem()
            }
            assertEquals(0, deletedState.activeHabits.size)
            assertNull(habitRepository.getHabitById("h_del"))
        }
    }

    @Test
    fun searchQuery_filtersByNameAndDescriptionCaseInsensitive() = runTest(testDispatcher) {
        val h1 = HabitEntity(id = "h1", name = "Morning Run", description = "5km in park", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 0, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        val h2 = HabitEntity(id = "h2", name = "Read Book", description = "Read chapter 1", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 1, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        habitRepository.createHabit(h1)
        habitRepository.createHabit(h2)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertEquals(2, state.filteredHabits.size)

            // Search by name substring "run"
            viewModel.onEvent(HabitListUiEvent.UpdateSearchQuery("run"))
            val searchRun = awaitItem()
            assertEquals(1, searchRun.filteredHabits.size)
            assertEquals("Morning Run", searchRun.filteredHabits[0].name)

            // Search by description substring "chapter"
            viewModel.onEvent(HabitListUiEvent.UpdateSearchQuery("CHAPTER"))
            val searchDesc = awaitItem()
            assertEquals(1, searchDesc.filteredHabits.size)
            assertEquals("Read Book", searchDesc.filteredHabits[0].name)

            // Clear search
            viewModel.onEvent(HabitListUiEvent.UpdateSearchQuery(""))
            val searchEmpty = awaitItem()
            assertEquals(2, searchEmpty.filteredHabits.size)
        }
    }

    @Test
    fun statusFilter_filtersActivePausedAndArchived() = runTest(testDispatcher) {
        val active = HabitEntity(id = "h_act", name = "Active", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 0, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        val paused = HabitEntity(id = "h_pau", name = "Paused", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 1, isPaused = true, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        val archived = HabitEntity(id = "h_arc", name = "Archived", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 2, isPaused = false, isArchived = true, createdAt = 1000L, updatedAt = 1000L)
        habitRepository.createHabit(active)
        habitRepository.createHabit(paused)
        habitRepository.createHabit(archived)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            // Default ALL shows all 3
            assertEquals(3, state.filteredHabits.size)

            // Filter ACTIVE
            viewModel.onEvent(HabitListUiEvent.SelectStatusFilter(HabitStatusFilter.ACTIVE))
            val actState = awaitItem()
            assertEquals(1, actState.filteredHabits.size)
            assertEquals("Active", actState.filteredHabits[0].name)

            // Filter PAUSED
            viewModel.onEvent(HabitListUiEvent.SelectStatusFilter(HabitStatusFilter.PAUSED))
            val pauState = awaitItem()
            assertEquals(1, pauState.filteredHabits.size)
            assertEquals("Paused", pauState.filteredHabits[0].name)

            // Filter ARCHIVED
            viewModel.onEvent(HabitListUiEvent.SelectStatusFilter(HabitStatusFilter.ARCHIVED))
            val arcState = awaitItem()
            assertEquals(1, arcState.filteredHabits.size)
            assertEquals("Archived", arcState.filteredHabits[0].name)
        }
    }

    @Test
    fun categoryFilter_andCategoryOperations() = runTest(testDispatcher) {
        // Create Category via event
        viewModel.onEvent(
            HabitListUiEvent.SaveCategory(
                id = null,
                name = "Fitness",
                colorHex = "#F08A6C",
                icon = "fitness"
            )
        )
        advanceUntilIdle()

        val cats = categoryRepository.getAllCategories()
        assertEquals(1, cats.size)
        val catId = cats[0].id

        val hWithCat = HabitEntity(id = "h1", name = "Gym", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 0, isPaused = false, isArchived = false, categoryId = catId, createdAt = 1000L, updatedAt = 1000L)
        val hNoCat = HabitEntity(id = "h2", name = "Meditate", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 1, isPaused = false, isArchived = false, categoryId = null, createdAt = 1000L, updatedAt = 1000L)
        habitRepository.createHabit(hWithCat)
        habitRepository.createHabit(hNoCat)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertEquals(2, state.filteredHabits.size)

            // Filter by Fitness category
            viewModel.onEvent(HabitListUiEvent.SelectCategoryFilter(catId))
            val filteredCat = awaitItem()
            assertEquals(1, filteredCat.filteredHabits.size)
            assertEquals("Gym", filteredCat.filteredHabits[0].name)
            assertEquals("Fitness", filteredCat.filteredHabits[0].categoryName)

            // Clear category filter
            viewModel.onEvent(HabitListUiEvent.SelectCategoryFilter(null))
            val all = awaitItem()
            assertEquals(2, all.filteredHabits.size)
        }
    }

    @Test
    fun presentationSorting_doesNotMutatePersistedDisplayOrder() = runTest(testDispatcher) {
        // Create habits with distinct names and initial display orders
        val h1 = HabitEntity(id = "h1", name = "Zebra Habit", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 0, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        val h2 = HabitEntity(id = "h2", name = "Alpha Habit", measurementType = "BOOLEAN", scheduleType = "DAILY", scheduleConfig = "{}", displayOrder = 1, isPaused = false, isArchived = false, createdAt = 1000L, updatedAt = 1000L)
        habitRepository.createHabit(h1)
        habitRepository.createHabit(h2)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            val manualState = awaitItem()
            // In MANUAL mode, h1 is first (displayOrder 0), h2 is second (displayOrder 1)
            assertEquals("Zebra Habit", manualState.filteredHabits[0].name)
            assertEquals("Alpha Habit", manualState.filteredHabits[1].name)

            // Switch presentation sort mode to NAME_ASC
            viewModel.onEvent(HabitListUiEvent.SelectSortMode(HabitSortMode.NAME_ASC))
            val sortedState = awaitItem()
            // Presentation list is sorted alphabetically
            assertEquals("Alpha Habit", sortedState.filteredHabits[0].name)
            assertEquals("Zebra Habit", sortedState.filteredHabits[1].name)

            // INVARIANT SAFEGUARD (Decision 6.2): Persisted displayOrder in DB MUST NOT be altered!
            val dbH1 = habitRepository.getHabitById("h1")
            val dbH2 = habitRepository.getHabitById("h2")
            assertEquals("Persisted displayOrder must remain 0 for h1", 0, dbH1?.displayOrder)
            assertEquals("Persisted displayOrder must remain 1 for h2", 1, dbH2?.displayOrder)

            // Switching back to MANUAL restores custom order
            viewModel.onEvent(HabitListUiEvent.SelectSortMode(HabitSortMode.MANUAL))
            val restoredState = awaitItem()
            assertEquals("Zebra Habit", restoredState.filteredHabits[0].name)
            assertEquals("Alpha Habit", restoredState.filteredHabits[1].name)
        }
    }
}
