package com.habit1.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyGoalLifecycleAndIntegrityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var dailyGoalRepository: DailyGoalRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        dailyGoalRepository = DailyGoalRepositoryImpl(database.dailyGoalDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun createGoal_withTitleNotesAndDate_persistsAccurately() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goal = DailyGoalEntity(
            id = "goal_create",
            title = "Read Clean Code",
            targetDate = "2026-09-21",
            isCompleted = false,
            displayOrder = 0,
            notes = "Chapters 1 to 3",
            createdAt = now,
            updatedAt = now
        )
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        val goals = dailyGoalRepository.getGoalsForDate("2026-09-21")
        assertEquals(1, goals.size)
        val domain = goals[0].toDomain()
        assertEquals("goal_create", domain.id)
        assertEquals("Read Clean Code", domain.title)
        assertEquals("Chapters 1 to 3", domain.notes)
        assertEquals(LocalDate.of(2026, 9, 21), domain.targetDate)
        assertFalse(domain.isCompleted)
    }

    @Test
    fun editGoal_preservesCompletionAndId() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goal = DailyGoalEntity(
            id = "goal_edit",
            title = "Old Title",
            targetDate = "2026-09-21",
            isCompleted = true, // already completed
            displayOrder = 0,
            notes = "Old notes",
            createdAt = now,
            updatedAt = now
        )
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        // Edit content
        dailyGoalRepository.updateGoalContent("goal_edit", "New Title", "Updated notes")
        advanceUntilIdle()

        val updated = dailyGoalRepository.getGoalById("goal_edit")
        assertNotNull(updated)
        assertEquals("New Title", updated!!.goal.title)
        assertEquals("Updated notes", updated.goal.notes)
        assertEquals("2026-09-21", updated.goal.targetDate)
        // Editing title/notes must preserve completion state
        assertTrue(updated.goal.isCompleted)
    }

    @Test
    fun moveGoalDate_completedGoalResetsToIncomplete() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goal = DailyGoalEntity(
            id = "goal_move_comp",
            title = "Study DBMS",
            targetDate = "2026-09-21",
            isCompleted = true,
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        // USER RULE: Moving a completed goal to another date resets isCompleted = false
        dailyGoalRepository.moveGoalDate("goal_move_comp", "2026-09-25", 0)
        advanceUntilIdle()

        val oldDateGoals = dailyGoalRepository.getGoalsForDate("2026-09-21")
        assertTrue(oldDateGoals.isEmpty())

        val newDateGoals = dailyGoalRepository.getGoalsForDate("2026-09-25")
        assertEquals(1, newDateGoals.size)
        val moved = newDateGoals[0].goal
        assertEquals("goal_move_comp", moved.id)
        assertEquals("2026-09-25", moved.targetDate)
        assertFalse("Moved completed goal must reset to incomplete", moved.isCompleted)
    }

    @Test
    fun moveGoalDate_incompleteGoalPreservesIncomplete() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goal = DailyGoalEntity(
            id = "goal_move_incomp",
            title = "Workout",
            targetDate = "2026-09-21",
            isCompleted = false,
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        dailyGoalRepository.createGoal(goal)
        advanceUntilIdle()

        dailyGoalRepository.moveGoalDate("goal_move_incomp", "2026-09-22", 0)
        advanceUntilIdle()

        val oldDateGoals = dailyGoalRepository.getGoalsForDate("2026-09-21")
        assertTrue(oldDateGoals.isEmpty())

        val newDateGoals = dailyGoalRepository.getGoalsForDate("2026-09-22")
        assertEquals(1, newDateGoals.size)
        assertFalse(newDateGoals[0].goal.isCompleted)
    }

    @Test
    fun reorderGoals_modifiesDisplayOrderAtomically() = runTest(testDispatcher) {
        val g1 = DailyGoalEntity(id = "g1", title = "First", targetDate = "2026-09-21", isCompleted = false, displayOrder = 0, createdAt = 1000L, updatedAt = 1000L)
        val g2 = DailyGoalEntity(id = "g2", title = "Second", targetDate = "2026-09-21", isCompleted = false, displayOrder = 1, createdAt = 1000L, updatedAt = 1000L)
        dailyGoalRepository.createGoal(g1)
        dailyGoalRepository.createGoal(g2)
        advanceUntilIdle()

        // Swap orders
        dailyGoalRepository.reorderGoals("2026-09-21", listOf("g2", "g1"))
        advanceUntilIdle()

        val goals = dailyGoalRepository.getGoalsForDate("2026-09-21")
        assertEquals(2, goals.size)
        assertEquals("g2", goals[0].goal.id)
        assertEquals(0, goals[0].goal.displayOrder)
        assertEquals("g1", goals[1].goal.id)
        assertEquals(1, goals[1].goal.displayOrder)
    }

    @Test
    fun subtaskOwnership_goalDeletionCascadesToSubtasksOnly() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goalA = DailyGoalEntity(id = "goalA", title = "Goal A", targetDate = "2026-09-21", isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        val subA1 = GoalSubtaskEntity(id = "subA1", goalId = "goalA", title = "A1", isCompleted = false, displayOrder = 0, createdAt = now)
        val subA2 = GoalSubtaskEntity(id = "subA2", goalId = "goalA", title = "A2", isCompleted = false, displayOrder = 1, createdAt = now)

        val goalB = DailyGoalEntity(id = "goalB", title = "Goal B", targetDate = "2026-09-21", isCompleted = false, displayOrder = 1, createdAt = now, updatedAt = now)
        val subB1 = GoalSubtaskEntity(id = "subB1", goalId = "goalB", title = "B1", isCompleted = false, displayOrder = 0, createdAt = now)

        dailyGoalRepository.createGoal(goalA)
        dailyGoalRepository.addSubtask(subA1)
        dailyGoalRepository.addSubtask(subA2)

        dailyGoalRepository.createGoal(goalB)
        dailyGoalRepository.addSubtask(subB1)
        advanceUntilIdle()

        // Verify initial state
        val initialA = dailyGoalRepository.getGoalById("goalA")
        assertEquals(2, initialA!!.subtasks.size)
        val initialB = dailyGoalRepository.getGoalById("goalB")
        assertEquals(1, initialB!!.subtasks.size)

        // Delete Goal A
        dailyGoalRepository.deleteGoal("goalA")
        advanceUntilIdle()

        // Goal A and its subtasks A1 and A2 must be deleted
        assertNull(dailyGoalRepository.getGoalById("goalA"))

        // Goal B and B1 must remain completely unaffected
        val remainingB = dailyGoalRepository.getGoalById("goalB")
        assertNotNull(remainingB)
        assertEquals("Goal B", remainingB!!.goal.title)
        assertEquals(1, remainingB.subtasks.size)
        assertEquals("subB1", remainingB.subtasks[0].id)
    }

    @Test
    fun subtaskOwnership_movingGoalMovesSubtasksWithIt() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goal = DailyGoalEntity(id = "goal_parent", title = "Parent", targetDate = "2026-09-21", isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        val sub1 = GoalSubtaskEntity(id = "sub1", goalId = "goal_parent", title = "Child 1", isCompleted = true, displayOrder = 0, createdAt = now)
        val sub2 = GoalSubtaskEntity(id = "sub2", goalId = "goal_parent", title = "Child 2", isCompleted = false, displayOrder = 1, createdAt = now)

        dailyGoalRepository.createGoal(goal)
        dailyGoalRepository.addSubtask(sub1)
        dailyGoalRepository.addSubtask(sub2)
        advanceUntilIdle()

        // Move Goal to tomorrow
        dailyGoalRepository.moveGoalDate("goal_parent", "2026-09-22", 0)
        advanceUntilIdle()

        val moved = dailyGoalRepository.getGoalById("goal_parent")
        assertNotNull(moved)
        assertEquals("2026-09-22", moved!!.goal.targetDate)
        assertEquals(2, moved.subtasks.size)
        assertEquals("sub1", moved.subtasks[0].id)
        assertTrue(moved.subtasks[0].isCompleted)
        assertEquals("sub2", moved.subtasks[1].id)
        assertFalse(moved.subtasks[1].isCompleted)
    }

    @Test
    fun subtaskLifecycle_addEditReorderDelete() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goal = DailyGoalEntity(id = "g_sub_cycle", title = "Project", targetDate = "2026-09-21", isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        dailyGoalRepository.createGoal(goal)

        val s1 = GoalSubtaskEntity(id = "s1", goalId = "g_sub_cycle", title = "Initial Task 1", isCompleted = false, displayOrder = 0, createdAt = now)
        val s2 = GoalSubtaskEntity(id = "s2", goalId = "g_sub_cycle", title = "Initial Task 2", isCompleted = false, displayOrder = 1, createdAt = now)
        dailyGoalRepository.addSubtask(s1)
        dailyGoalRepository.addSubtask(s2)
        advanceUntilIdle()

        // Edit s1 title
        dailyGoalRepository.updateSubtaskTitle("s1", "Refined Task 1")
        advanceUntilIdle()

        val afterEdit = dailyGoalRepository.getGoalById("g_sub_cycle")!!.toDomain()
        assertEquals("Refined Task 1", afterEdit.subtasks[0].title)

        // Reorder subtasks
        dailyGoalRepository.reorderSubtasks("g_sub_cycle", listOf("s2", "s1"))
        advanceUntilIdle()

        val afterReorder = dailyGoalRepository.getGoalById("g_sub_cycle")!!.toDomain()
        assertEquals("s2", afterReorder.subtasks[0].id)
        assertEquals("s1", afterReorder.subtasks[1].id)

        // Delete s2
        dailyGoalRepository.deleteSubtask("s2")
        advanceUntilIdle()

        val afterDelete = dailyGoalRepository.getGoalById("g_sub_cycle")!!.toDomain()
        assertEquals(1, afterDelete.subtasks.size)
        assertEquals("s1", afterDelete.subtasks[0].id)
    }

    @Test
    fun independentCompletionSemantics_subtasksDoNotAutoCompleteParentAndViceVersa() = runTest(testDispatcher) {
        val now = 1700000000000L
        val goal = DailyGoalEntity(id = "g_indep", title = "Independent Goal", targetDate = "2026-09-21", isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        val s1 = GoalSubtaskEntity(id = "s1_indep", goalId = "g_indep", title = "Step 1", isCompleted = false, displayOrder = 0, createdAt = now)
        val s2 = GoalSubtaskEntity(id = "s2_indep", goalId = "g_indep", title = "Step 2", isCompleted = false, displayOrder = 1, createdAt = now)
        dailyGoalRepository.createGoal(goal)
        dailyGoalRepository.addSubtask(s1)
        dailyGoalRepository.addSubtask(s2)
        advanceUntilIdle()

        // Complete both subtasks
        dailyGoalRepository.setSubtaskCompleted("s1_indep", true)
        dailyGoalRepository.setSubtaskCompleted("s2_indep", true)
        advanceUntilIdle()

        // USER RULE: Subtasks completion does NOT automatically complete parent goal
        val goalState1 = dailyGoalRepository.getGoalById("g_indep")!!.goal
        assertFalse("Goal must remain incomplete even when all subtasks are finished", goalState1.isCompleted)

        // Complete parent goal explicitly
        dailyGoalRepository.setGoalCompleted("g_indep", true)
        advanceUntilIdle()

        // Uncomplete one subtask
        dailyGoalRepository.setSubtaskCompleted("s1_indep", false)
        advanceUntilIdle()

        // USER RULE: Subtask change does NOT silently uncomplete parent goal
        val goalState2 = dailyGoalRepository.getGoalById("g_indep")!!.goal
        assertTrue("Goal must remain complete until user explicitly toggles it", goalState2.isCompleted)
    }

    @Test
    fun dateIsolation_goalsDoNotLeakAcrossDates() = runTest(testDispatcher) {
        val now = 1700000000000L
        val pastGoal = DailyGoalEntity(id = "g_past", title = "Past Goal", targetDate = "2026-09-20", isCompleted = true, displayOrder = 0, createdAt = now, updatedAt = now)
        val todayGoal = DailyGoalEntity(id = "g_today", title = "Today Goal", targetDate = "2026-09-21", isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)
        val futureGoal = DailyGoalEntity(id = "g_future", title = "Future Goal", targetDate = "2026-09-22", isCompleted = false, displayOrder = 0, createdAt = now, updatedAt = now)

        dailyGoalRepository.createGoal(pastGoal)
        dailyGoalRepository.createGoal(todayGoal)
        dailyGoalRepository.createGoal(futureGoal)
        advanceUntilIdle()

        val pastList = dailyGoalRepository.getGoalsForDate("2026-09-20")
        assertEquals(1, pastList.size)
        assertEquals("Past Goal", pastList[0].goal.title)

        val todayList = dailyGoalRepository.getGoalsForDate("2026-09-21")
        assertEquals(1, todayList.size)
        assertEquals("Today Goal", todayList[0].goal.title)

        val futureList = dailyGoalRepository.getGoalsForDate("2026-09-22")
        assertEquals(1, futureList.size)
        assertEquals("Future Goal", futureList[0].goal.title)
    }
}
