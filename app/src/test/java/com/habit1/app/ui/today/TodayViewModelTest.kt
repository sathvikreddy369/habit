package com.habit1.app.ui.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository
    private lateinit var dailyGoalRepository: DailyGoalRepository
    private lateinit var viewModel: TodayViewModel

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)

        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        habitRecordRepository = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
        dailyGoalRepository = DailyGoalRepositoryImpl(database.dailyGoalDao(), testDispatcher)

        viewModel = TodayViewModel(
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            dailyGoalRepository = dailyGoalRepository,
            zoneId = zoneId
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun initialState_isEmptyWhenNoHabitsOrGoals() = runTest(testDispatcher) {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(0, loaded.habits.size)
            assertEquals(0, loaded.goals.size)
            assertEquals(0.0f, loaded.overallProgress, 0.001f)
        }
    }

    @Test
    fun habitScheduledForToday_isIncludedInUiState() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial loading state
            awaitItem() // empty state

            // Create habit created in the past (yesterday)
            val past = System.currentTimeMillis() - 86400000
            val habit = HabitEntity(
                id = "habit-daily",
                name = "Morning Stretch",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                displayOrder = 0,
                isArchived = false,
                createdAt = past,
                updatedAt = past
            )
            habitRepository.createHabit(habit)

            val state = awaitItem()
            assertEquals(1, state.habits.size)
            assertEquals("Morning Stretch", state.habits[0].name)
            assertFalse(state.habits[0].isCompleted)
            assertEquals(1, state.totalScheduledHabitsCount)
            assertEquals(0, state.completedHabitsCount)
        }
    }

    @Test
    fun toggleBooleanHabit_updatesCompletionAndProgress() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            val past = System.currentTimeMillis() - 86400000
            val habit = HabitEntity(
                id = "h_bool",
                name = "Floss",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                displayOrder = 0,
                isArchived = false,
                createdAt = past,
                updatedAt = past
            )
            habitRepository.createHabit(habit)

            val insertedState = awaitItem()
            assertFalse(insertedState.habits[0].isCompleted)

            // Toggle Done
            viewModel.onEvent(TodayUiEvent.ToggleHabit("h_bool"))

            val doneState = awaitItem()
            assertTrue(doneState.habits[0].isCompleted)
            assertEquals(1, doneState.completedHabitsCount)
            assertEquals(1.0f, doneState.overallProgress, 0.001f)

            // Toggle Undone
            viewModel.onEvent(TodayUiEvent.ToggleHabit("h_bool"))

            val undoneState = awaitItem()
            assertFalse(undoneState.habits[0].isCompleted)
            assertEquals(0, undoneState.completedHabitsCount)
            assertEquals(0.0f, undoneState.overallProgress, 0.001f)
        }
    }

    @Test
    fun quantitativeHabit_incrementAndDecrementInteractions() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            val past = System.currentTimeMillis() - 86400000
            val habit = HabitEntity(
                id = "h_count",
                name = "Pushups",
                measurementType = "COUNT",
                targetValue = 2.0,
                unit = "reps",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                displayOrder = 0,
                isArchived = false,
                createdAt = past,
                updatedAt = past
            )
            habitRepository.createHabit(habit)
            awaitItem()

            // Increment 1
            viewModel.onEvent(TodayUiEvent.IncrementHabit("h_count"))

            val step1 = awaitItem()
            assertEquals(1.0, step1.habits[0].actualValue, 0.001)
            assertFalse(step1.habits[0].isCompleted)
            assertEquals(0.5f, step1.habits[0].progressRatio, 0.001f)

            // Increment 2 (reaches target)
            viewModel.onEvent(TodayUiEvent.IncrementHabit("h_count"))

            val step2 = awaitItem()
            assertEquals(2.0, step2.habits[0].actualValue, 0.001)
            assertTrue(step2.habits[0].isCompleted)
            assertEquals(1.0f, step2.habits[0].progressRatio, 0.001f)

            // Decrement 1
            viewModel.onEvent(TodayUiEvent.DecrementHabit("h_count"))

            val step3 = awaitItem()
            assertEquals(1.0, step3.habits[0].actualValue, 0.001)
            assertFalse(step3.habits[0].isCompleted)
        }
    }

    @Test
    fun dailyGoals_toggleAndSubtaskInteractions() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            val todayStr = DateTimeUtils.formatDate(DateTimeUtils.today(zoneId))
            val now = System.currentTimeMillis()

            val goal = DailyGoalEntity(
                id = "goal_1",
                title = "Ship Phase 4",
                targetDate = todayStr,
                isCompleted = false,
                displayOrder = 0,
                createdAt = now,
                updatedAt = now
            )
            val subtask = GoalSubtaskEntity(
                id = "sub_1",
                goalId = "goal_1",
                title = "Write tests",
                isCompleted = false,
                displayOrder = 0,
                createdAt = now
            )
            dailyGoalRepository.createGoal(goal)
            val withGoal = awaitItem()
            assertEquals(1, withGoal.goals.size)

            dailyGoalRepository.addSubtask(subtask)
            val withSubtask = awaitItem()
            assertEquals(1, withSubtask.goals[0].subtasks.size)
            assertFalse(withSubtask.goals[0].subtasks[0].isCompleted)

            // Toggle Subtask
            viewModel.onEvent(TodayUiEvent.ToggleSubtask("goal_1", "sub_1"))
            val subtaskToggled = awaitItem()
            assertTrue(subtaskToggled.goals[0].subtasks[0].isCompleted)

            // Toggle Goal
            viewModel.onEvent(TodayUiEvent.ToggleGoal("goal_1"))
            val goalToggled = awaitItem()
            assertTrue(goalToggled.goals[0].isCompleted)
            assertEquals(1, goalToggled.completedGoalsCount)
        }
    }

    @Test
    fun addGoalEvent_createsNewGoalForToday() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            viewModel.onEvent(TodayUiEvent.AddGoal("Read 1 chapter"))

            val state = awaitItem()
            assertEquals(1, state.goals.size)
            assertEquals("Read 1 chapter", state.goals[0].title)
            assertFalse(state.goals[0].isCompleted)
        }
    }

    @Test
    fun setHabitValue_explicitValueEntry() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            val past = System.currentTimeMillis() - 86400000
            val habit = HabitEntity(
                id = "h_water",
                name = "Water",
                measurementType = "QUANTITY",
                targetValue = 2.5,
                unit = "L",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                displayOrder = 0,
                isArchived = false,
                createdAt = past,
                updatedAt = past
            )
            habitRepository.createHabit(habit)
            awaitItem()

            viewModel.onEvent(TodayUiEvent.SetHabitValue("h_water", 2.5))
            val updated = awaitItem()
            assertEquals(2.5, updated.habits[0].actualValue, 0.001)
            assertTrue(updated.habits[0].isCompleted)
        }
    }

    @Test
    fun addHabitQuick_createsNewActiveDailyHabit() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            viewModel.onEvent(
                TodayUiEvent.AddHabitQuick(
                    name = "Meditation",
                    measurementType = com.habit1.app.domain.model.MeasurementType.BooleanChoice
                )
            )

            val state = awaitItem()
            assertEquals(1, state.habits.size)
            assertEquals("Meditation", state.habits[0].name)
        }
    }
}

