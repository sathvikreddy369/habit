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
import com.habit1.app.data.repository.DailyReviewRepository
import com.habit1.app.data.repository.DailyReviewRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository
    private lateinit var dailyGoalRepository: DailyGoalRepository
    private lateinit var dailyReviewRepository: DailyReviewRepository
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
        dailyReviewRepository = DailyReviewRepositoryImpl(database.dailyReviewDao(), testDispatcher)

        viewModel = TodayViewModel(
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            dailyGoalRepository = dailyGoalRepository,
            dailyReviewRepository = dailyReviewRepository,
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
    fun setHabitValue_countAndDurationCompletionAndSteppers() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            val past = System.currentTimeMillis() - 86400000
            val countHabit = HabitEntity(
                id = "h_pushups",
                name = "Pushups",
                measurementType = "COUNT",
                targetValue = 50.0,
                unit = "reps",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                displayOrder = 0,
                isArchived = false,
                createdAt = past,
                updatedAt = past
            )
            habitRepository.createHabit(countHabit)
            awaitItem()

            // Type 20 directly -> under target, not completed
            viewModel.onEvent(TodayUiEvent.SetHabitValue("h_pushups", 20.0))
            val state1 = awaitItem()
            assertEquals(20.0, state1.habits[0].actualValue, 0.001)
            assertFalse(state1.habits[0].isCompleted)

            // Increment stepper from 20 -> 21
            viewModel.onEvent(TodayUiEvent.IncrementHabit("h_pushups"))
            val state2 = awaitItem()
            assertEquals(21.0, state2.habits[0].actualValue, 0.001)
            assertFalse(state2.habits[0].isCompleted)

            // Type 50 directly -> reaches target, completed!
            viewModel.onEvent(TodayUiEvent.SetHabitValue("h_pushups", 50.0))
            val state3 = awaitItem()
            assertEquals(50.0, state3.habits[0].actualValue, 0.001)
            assertTrue(state3.habits[0].isCompleted)

            // Decrement stepper from 50 -> 49, no longer completed
            viewModel.onEvent(TodayUiEvent.DecrementHabit("h_pushups"))
            val state4 = awaitItem()
            assertEquals(49.0, state4.habits[0].actualValue, 0.001)
            assertFalse(state4.habits[0].isCompleted)
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

    @Test
    fun saveNewGoal_createsGoalWithNotes() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // empty

            viewModel.onEvent(TodayUiEvent.SaveNewGoal(title = "Review PR", notes = "Security critical"))
            val state = awaitItem()
            assertEquals(1, state.goals.size)
            assertEquals("Review PR", state.goals[0].title)
            assertEquals("Security critical", state.goals[0].notes)
        }
    }

    @Test
    fun editGoal_updatesTitleAndNotes() = runTest(testDispatcher) {
        val todayStr = DateTimeUtils.formatDate(DateTimeUtils.today(zoneId))
        val now = System.currentTimeMillis()
        val goal = DailyGoalEntity(id = "g_edit_vm", title = "Initial", targetDate = todayStr, isCompleted = true, displayOrder = 0, createdAt = now, updatedAt = now)
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            assertEquals("Initial", initial.goals[0].title)

            viewModel.onEvent(TodayUiEvent.SaveEditedGoal(goalId = "g_edit_vm", title = "Updated Title", notes = "New note"))
            val edited = awaitItem()
            assertEquals("Updated Title", edited.goals[0].title)
            assertEquals("New note", edited.goals[0].notes)
            assertTrue("Completion must be preserved across edits", edited.goals[0].isCompleted)
        }
    }

    @Test
    fun deleteGoal_withConfirmationRemovesGoal() = runTest(testDispatcher) {
        val todayStr = DateTimeUtils.formatDate(DateTimeUtils.today(zoneId))
        val now = System.currentTimeMillis()
        val goal = DailyGoalEntity(id = "g_del_vm", title = "Delete Me", targetDate = todayStr, isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            assertEquals(1, initial.goals.size)

            viewModel.onEvent(TodayUiEvent.RequestDeleteGoal(initial.goals[0]))
            val requestState = awaitItem()
            assertNotNull(requestState.goalPendingDeletion)

            viewModel.onEvent(TodayUiEvent.ConfirmDeleteGoal)
            val deletedState = awaitItem()
            assertEquals(0, deletedState.goals.size)
            assertNull(deletedState.goalPendingDeletion)
        }
    }

    @Test
    fun moveGoalDate_removesFromTodayView() = runTest(testDispatcher) {
        val today = DateTimeUtils.today(zoneId)
        val todayStr = DateTimeUtils.formatDate(today)
        val now = System.currentTimeMillis()
        val goal = DailyGoalEntity(id = "g_move_vm", title = "Move Me", targetDate = todayStr, isCompleted = true, displayOrder = 0, createdAt = now, updatedAt = now)
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            assertEquals(1, initial.goals.size)

            viewModel.onEvent(TodayUiEvent.MoveGoalDate("g_move_vm", today.plusDays(1)))
            val movedState = awaitItem()
            assertEquals(0, movedState.goals.size)
        }
    }

    @Test
    fun reorderGoals_moveUpAndDown() = runTest(testDispatcher) {
        val todayStr = DateTimeUtils.formatDate(DateTimeUtils.today(zoneId))
        val now = System.currentTimeMillis()
        val g1 = DailyGoalEntity(id = "g1_order", title = "First", targetDate = todayStr, isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        val g2 = DailyGoalEntity(id = "g2_order", title = "Second", targetDate = todayStr, isCompleted = false, displayOrder = 1, createdAt = now, updatedAt = now)
        dailyGoalRepository.createGoal(g1)
        dailyGoalRepository.createGoal(g2)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            assertEquals("First", initial.goals[0].title)
            assertEquals("Second", initial.goals[1].title)

            viewModel.onEvent(TodayUiEvent.MoveGoalUp("g2_order"))
            val reordered = awaitItem()
            assertEquals("Second", reordered.goals[0].title)
            assertEquals("First", reordered.goals[1].title)

            viewModel.onEvent(TodayUiEvent.MoveGoalDown("g2_order"))
            val back = awaitItem()
            assertEquals("First", back.goals[0].title)
            assertEquals("Second", back.goals[1].title)
        }
    }

    @Test
    fun subtaskManagement_addEditReorderDelete() = runTest(testDispatcher) {
        val todayStr = DateTimeUtils.formatDate(DateTimeUtils.today(zoneId))
        val now = System.currentTimeMillis()
        val goal = DailyGoalEntity(id = "g_sub_vm", title = "Parent Goal", targetDate = todayStr, isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // goal loaded with 0 subtasks

            // Add Subtask 1
            viewModel.onEvent(TodayUiEvent.AddSubtask("g_sub_vm", "Sub 1"))
            val s1State = awaitItem()
            assertEquals(1, s1State.goals[0].subtasks.size)
            assertEquals("Sub 1", s1State.goals[0].subtasks[0].title)

            // Add Subtask 2
            viewModel.onEvent(TodayUiEvent.AddSubtask("g_sub_vm", "Sub 2"))
            val s2State = awaitItem()
            assertEquals(2, s2State.goals[0].subtasks.size)
            val sub1Id = s2State.goals[0].subtasks[0].id
            val sub2Id = s2State.goals[0].subtasks[1].id

            // Edit Subtask 1
            viewModel.onEvent(TodayUiEvent.SaveEditedSubtask("g_sub_vm", sub1Id, "Sub 1 Edited"))
            val editedState = awaitItem()
            assertEquals("Sub 1 Edited", editedState.goals[0].subtasks[0].title)

            // Reorder Subtasks: move sub2 up
            viewModel.onEvent(TodayUiEvent.MoveSubtaskUp("g_sub_vm", sub2Id))
            val reorderedState = awaitItem()
            assertEquals(sub2Id, reorderedState.goals[0].subtasks[0].id)
            assertEquals(sub1Id, reorderedState.goals[0].subtasks[1].id)

            // Delete Subtask 2
            viewModel.onEvent(TodayUiEvent.DeleteSubtask("g_sub_vm", sub2Id))
            val deletedSubState = awaitItem()
            assertEquals(1, deletedSubState.goals[0].subtasks.size)
            assertEquals(sub1Id, deletedSubState.goals[0].subtasks[0].id)
        }
    }

    @Test
    fun setHabitValue_negativeValueCoercedToZero() = runTest(testDispatcher) {
        val past = System.currentTimeMillis() - 86400000
        val habit = HabitEntity(
            id = "h_neg",
            name = "Water",
            measurementType = "QUANTITY",
            targetValue = 2.0,
            unit = "L",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isArchived = false,
            createdAt = past,
            updatedAt = past
        )
        habitRepository.createHabit(habit)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // habit loaded

            // Set to 1.0 first
            viewModel.onEvent(TodayUiEvent.SetHabitValue("h_neg", 1.0))
            val state1 = awaitItem()
            assertEquals(1.0, state1.habits[0].actualValue, 0.001)

            // Set negative value -> coerced to 0.0
            viewModel.onEvent(TodayUiEvent.SetHabitValue("h_neg", -3.0))
            val stateCoerced = awaitItem()
            assertEquals(0.0, stateCoerced.habits[0].actualValue, 0.001)
        }
    }

    @Test
    fun dailyReview_dialogStateFlow() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            assertFalse(initial.isReviewDialogOpen)

            viewModel.onEvent(TodayUiEvent.OpenReviewDialog)
            val openState = awaitItem()
            assertTrue(openState.isReviewDialogOpen)

            viewModel.onEvent(TodayUiEvent.DismissReviewDialog)
            val closeState = awaitItem()
            assertFalse(closeState.isReviewDialogOpen)
        }
    }

    @Test
    fun dailyReview_saveAndObserveAndCleanFlow() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            assertNull(initial.dailyReview)

            // Save review
            viewModel.onEvent(TodayUiEvent.SaveReview("Felt great today!", "Energized"))
            advanceUntilIdle()

            val stateWithReview = awaitItem()
            assertNotNull(stateWithReview.dailyReview)
            assertEquals("Felt great today!", stateWithReview.dailyReview!!.notes)
            assertEquals("Energized", stateWithReview.dailyReview!!.mood)
            assertFalse(stateWithReview.isReviewDialogOpen)

            // Delete review
            viewModel.onEvent(TodayUiEvent.DeleteReview)
            advanceUntilIdle()

            val stateAfterDelete = awaitItem()
            assertNull(stateAfterDelete.dailyReview)
            assertFalse(stateAfterDelete.isReviewDialogOpen)
        }
    }
}


