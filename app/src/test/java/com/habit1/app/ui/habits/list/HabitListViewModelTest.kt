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
    private lateinit var viewModel: HabitListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        viewModel = HabitListViewModel(habitRepository)
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
}
