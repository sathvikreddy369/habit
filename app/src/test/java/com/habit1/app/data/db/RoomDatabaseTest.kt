package com.habit1.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.dao.DailyGoalDao
import com.habit1.app.data.local.db.dao.DailyReviewDao
import com.habit1.app.data.local.db.dao.HabitDao
import com.habit1.app.data.local.db.dao.HabitRecordDao
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var recordDao: HabitRecordDao
    private lateinit var goalDao: DailyGoalDao
    private lateinit var reviewDao: DailyReviewDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitDao = database.habitDao()
        recordDao = database.habitRecordDao()
        goalDao = database.dailyGoalDao()
        reviewDao = database.dailyReviewDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- 1. Empty Database Scenarios ---
    @Test
    fun testEmptyDatabaseQueries() = runBlocking {
        assertEquals(0, habitDao.getActiveHabitsList().size)
        assertEquals(0, habitDao.observeActiveHabits().first().size)
        assertNull(habitDao.getById("non_existent_id"))
        assertEquals(0, recordDao.getRecordsForDate("2026-09-20").size)
        assertEquals(0, goalDao.getGoalsForDate("2026-09-20").size)
        assertNull(reviewDao.getReview("2026-09-20"))
    }

    // --- 2. Habit Lifecycle & Ordering ---
    @Test
    fun testHabitLifecycle() = runBlocking {
        val now = System.currentTimeMillis()
        val habit1 = HabitEntity(
            id = "h1",
            name = "Exercise",
            description = "Daily morning workout",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "07:30",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        val habit2 = HabitEntity(
            id = "h2",
            name = "Read Books",
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "mins",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 1,
            isPaused = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )

        habitDao.insert(habit1)
        habitDao.insert(habit2)

        var activeHabits = habitDao.getActiveHabitsList()
        assertEquals(2, activeHabits.size)
        assertEquals("h1", activeHabits[0].id)
        assertEquals("h2", activeHabits[1].id)

        // Reordering
        habitDao.updateDisplayOrder("h1", 1, now + 1)
        habitDao.updateDisplayOrder("h2", 0, now + 1)
        activeHabits = habitDao.getActiveHabitsList()
        assertEquals("h2", activeHabits[0].id)
        assertEquals("h1", activeHabits[1].id)

        // Pausing a habit
        habitDao.setPaused("h2", true, now + 2)
        val fetchedH2 = habitDao.getById("h2")
        assertNotNull(fetchedH2)
        assertTrue(fetchedH2!!.isPaused)

        // Archiving habit
        habitDao.setArchived("h1", true, now + 3)
        activeHabits = habitDao.getActiveHabitsList()
        assertEquals(1, activeHabits.size)
        assertEquals("h2", activeHabits[0].id)

        val allHabits = habitDao.getAllHabitsList()
        assertEquals(2, allHabits.size)
    }

    // --- 3. Measurement Type Changes & Snapshotting ---
    @Test
    fun testMeasurementSnapshottingPreservesHistoricalTruth() = runBlocking {
        val now = System.currentTimeMillis()
        val habit = HabitEntity(
            id = "water_habit",
            name = "Drink Water",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            createdAt = now,
            updatedAt = now
        )
        habitDao.insert(habit)

        // Record on day 1 with boolean measurement
        val recordDay1 = HabitRecordEntity(
            id = "rec1",
            habitId = "water_habit",
            date = "2026-01-01",
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            unit = null,
            isCompleted = true,
            recordedAt = now
        )
        recordDao.upsert(recordDay1)

        // Later, user changes habit measurement to QUANTITY (2.5 Liters)
        val updatedHabit = habit.copy(
            measurementType = "QUANTITY",
            targetValue = 2.5,
            unit = "L",
            updatedAt = now + 1000
        )
        habitDao.update(updatedHabit)

        // Record on day 2 with new quantity measurement
        val recordDay2 = HabitRecordEntity(
            id = "rec2",
            habitId = "water_habit",
            date = "2026-01-02",
            actualValue = 2.5,
            targetValue = 2.5,
            measurementType = "QUANTITY",
            unit = "L",
            isCompleted = true,
            recordedAt = now + 1000
        )
        recordDao.upsert(recordDay2)

        // Verify day 1 record was NOT altered or reinterpreted by the habit definition update
        val fetchedDay1 = recordDao.getRecord("water_habit", "2026-01-01")
        assertNotNull(fetchedDay1)
        assertEquals("BOOLEAN", fetchedDay1!!.measurementType)
        assertEquals(1.0, fetchedDay1.actualValue, 0.001)
        assertEquals(1.0, fetchedDay1.targetValue, 0.001)
        assertNull(fetchedDay1.unit)
        assertTrue(fetchedDay1.isCompleted)

        // Verify day 2 record has the new measurement snapshot
        val fetchedDay2 = recordDao.getRecord("water_habit", "2026-01-02")
        assertNotNull(fetchedDay2)
        assertEquals("QUANTITY", fetchedDay2!!.measurementType)
        assertEquals(2.5, fetchedDay2.actualValue, 0.001)
        assertEquals("L", fetchedDay2.unit)
        assertTrue(fetchedDay2.isCompleted)
    }

    // --- 4. Uniqueness & Overwrite Behavior ---
    @Test
    fun testRecordUniquenessPerDate() = runBlocking {
        val now = System.currentTimeMillis()
        habitDao.insert(
            HabitEntity(
                id = "h_unique",
                name = "Study",
                measurementType = "DURATION",
                targetValue = 60.0,
                unit = "mins",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                createdAt = now,
                updatedAt = now
            )
        )

        val recordInitial = HabitRecordEntity(
            id = "r_init",
            habitId = "h_unique",
            date = "2026-09-20",
            actualValue = 30.0,
            targetValue = 60.0,
            measurementType = "DURATION",
            unit = "mins",
            isCompleted = false,
            recordedAt = now
        )
        recordDao.upsert(recordInitial)

        assertEquals(1, recordDao.getRecordsForDate("2026-09-20").size)
        assertEquals(30.0, recordDao.getRecord("h_unique", "2026-09-20")!!.actualValue, 0.001)

        // User updates progress on the same date
        val recordUpdated = HabitRecordEntity(
            id = "r_updated",
            habitId = "h_unique",
            date = "2026-09-20",
            actualValue = 60.0,
            targetValue = 60.0,
            measurementType = "DURATION",
            unit = "mins",
            isCompleted = true,
            recordedAt = now + 500
        )
        recordDao.upsert(recordUpdated)

        // Still exactly 1 record for this habit on this date
        val records = recordDao.getRecordsForDate("2026-09-20")
        assertEquals(1, records.size)
        assertEquals(60.0, records[0].actualValue, 0.001)
        assertTrue(records[0].isCompleted)
    }

    // --- 5. Foreign Key Cascade Behavior ---
    @Test
    fun testForeignKeyCascadeOnHabitDeletion() = runBlocking {
        val now = System.currentTimeMillis()
        habitDao.insert(
            HabitEntity(
                id = "h_to_delete",
                name = "Temporary",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                createdAt = now,
                updatedAt = now
            )
        )

        recordDao.upsert(
            HabitRecordEntity(
                id = "r1",
                habitId = "h_to_delete",
                date = "2026-09-20",
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                isCompleted = true,
                recordedAt = now
            )
        )

        assertEquals(1, recordDao.getRecordsForDate("2026-09-20").size)

        // Deleting habit cascades to records
        habitDao.deleteById("h_to_delete")

        assertEquals(0, habitDao.getActiveHabitsList().size)
        assertEquals(0, recordDao.getRecordsForDate("2026-09-20").size)
        assertNull(recordDao.getRecord("h_to_delete", "2026-09-20"))
    }

    // --- 6. Date Windowing & Query Isolation ---
    @Test
    fun testDateWindowedQueries() = runBlocking {
        val now = System.currentTimeMillis()
        habitDao.insert(
            HabitEntity(
                id = "h_range",
                name = "Habit",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                createdAt = now,
                updatedAt = now
            )
        )

        val dates = listOf("2026-01-01", "2026-01-05", "2026-01-10", "2026-01-15", "2026-02-01")
        dates.forEachIndexed { index, d ->
            recordDao.upsert(
                HabitRecordEntity(
                    id = "rec_$index",
                    habitId = "h_range",
                    date = d,
                    actualValue = 1.0,
                    targetValue = 1.0,
                    measurementType = "BOOLEAN",
                    isCompleted = true,
                    recordedAt = now
                )
            )
        }

        // Query only January 5 to January 12
        val rangeRecords = recordDao.getRecordsForDateRange("2026-01-05", "2026-01-12")
        assertEquals(2, rangeRecords.size)
        assertEquals("2026-01-05", rangeRecords[0].date)
        assertEquals("2026-01-10", rangeRecords[1].date)
    }

    // --- 7. Daily Goals & Subtasks ---
    @Test
    fun testDailyGoalsAndSubtasks() = runBlocking {
        val now = System.currentTimeMillis()
        val goal1 = DailyGoalEntity(
            id = "g1",
            title = "Complete project README",
            targetDate = "2026-09-20",
            isCompleted = false,
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        val goal2 = DailyGoalEntity(
            id = "g2",
            title = "Solve 3 DSA problems",
            targetDate = "2026-09-20",
            isCompleted = false,
            displayOrder = 1,
            createdAt = now,
            updatedAt = now
        )

        goalDao.insertGoal(goal1)
        goalDao.insertGoal(goal2)

        // Subtasks under goal 1
        val subtask1 = GoalSubtaskEntity(
            id = "sub1",
            goalId = "g1",
            title = "Write installation steps",
            isCompleted = true,
            displayOrder = 0,
            createdAt = now
        )
        val subtask2 = GoalSubtaskEntity(
            id = "sub2",
            goalId = "g1",
            title = "Add architectural diagram",
            isCompleted = false,
            displayOrder = 1,
            createdAt = now
        )
        goalDao.insertSubtask(subtask1)
        goalDao.insertSubtask(subtask2)

        // Query goals for date with subtasks
        val goalsWithSubtasks = goalDao.getGoalsForDate("2026-09-20")
        assertEquals(2, goalsWithSubtasks.size)
        assertEquals("g1", goalsWithSubtasks[0].goal.id)
        assertEquals(2, goalsWithSubtasks[0].subtasks.size)
        assertEquals("Write installation steps", goalsWithSubtasks[0].subtasks[0].title)
        assertTrue(goalsWithSubtasks[0].subtasks[0].isCompleted)
        assertFalse(goalsWithSubtasks[0].subtasks[1].isCompleted)

        // Mark subtask 2 completed
        goalDao.updateSubtaskCompletion("sub2", true)
        val fetchedGoal = goalDao.getGoalWithSubtasksById("g1")
        assertNotNull(fetchedGoal)
        assertTrue(fetchedGoal!!.subtasks[1].isCompleted)

        // Reorder goals
        goalDao.updateGoalOrder("g1", 1, now + 10)
        goalDao.updateGoalOrder("g2", 0, now + 10)
        val reorderedGoals = goalDao.getGoalsForDate("2026-09-20")
        assertEquals("g2", reorderedGoals[0].goal.id)
        assertEquals("g1", reorderedGoals[1].goal.id)

        // Move goal 2 to tomorrow
        goalDao.moveGoalDate("g2", "2026-09-21", 0, now + 20)
        val sept20Goals = goalDao.getGoalsForDate("2026-09-20")
        assertEquals(1, sept20Goals.size)
        assertEquals("g1", sept20Goals[0].goal.id)

        val sept21Goals = goalDao.getGoalsForDate("2026-09-21")
        assertEquals(1, sept21Goals.size)
        assertEquals("g2", sept21Goals[0].goal.id)

        // Deleting goal 1 cascades to subtasks
        goalDao.deleteGoalById("g1")
        assertNull(goalDao.getGoalById("g1"))
        val remainingGoals = goalDao.getGoalsForDate("2026-09-20")
        assertEquals(0, remainingGoals.size)
    }

    @Test
    fun testDailyGoalStorageLifecycleCleanup() = runBlocking {
        val now = System.currentTimeMillis()

        // Insert a regular habit and record to verify they remain untouched
        val habit = HabitEntity(
            id = "habit_perm",
            name = "Permanent Habit",
            description = null,
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        habitDao.insert(habit)
        val record = HabitRecordEntity(
            id = "rec_perm",
            habitId = "habit_perm",
            date = "2026-09-10",
            isCompleted = true,
            actualValue = 1.0,
            targetValue = 1.0,
            unit = null,
            measurementType = "BOOLEAN",
            recordedAt = now
        )
        recordDao.upsert(record)

        // 1. Past Completed Goal (targetDate = 2026-09-10, isCompleted = true) -> ELIGIBLE for cleanup
        val pastCompletedGoal = DailyGoalEntity(
            id = "goal_past_comp",
            title = "Past Completed Goal",
            targetDate = "2026-09-10",
            isCompleted = true,
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        goalDao.insertGoal(pastCompletedGoal)
        val subtaskPastComp = GoalSubtaskEntity(
            id = "sub_past_comp",
            goalId = "goal_past_comp",
            title = "Subtask of past completed goal",
            isCompleted = true,
            displayOrder = 0,
            createdAt = now
        )
        goalDao.insertSubtask(subtaskPastComp)

        // 2. Past Incomplete Goal (targetDate = 2026-09-10, isCompleted = false) -> PRESERVED per PRD Section 7
        val pastIncompleteGoal = DailyGoalEntity(
            id = "goal_past_incomp",
            title = "Past Incomplete Goal",
            targetDate = "2026-09-10",
            isCompleted = false,
            displayOrder = 1,
            createdAt = now,
            updatedAt = now
        )
        goalDao.insertGoal(pastIncompleteGoal)
        val subtaskPastIncomp = GoalSubtaskEntity(
            id = "sub_past_incomp",
            goalId = "goal_past_incomp",
            title = "Subtask of past incomplete goal",
            isCompleted = false,
            displayOrder = 0,
            createdAt = now
        )
        goalDao.insertSubtask(subtaskPastIncomp)

        // 3. Today Completed Goal (targetDate = 2026-09-22, isCompleted = true) -> PRESERVED
        val todayCompletedGoal = DailyGoalEntity(
            id = "goal_today_comp",
            title = "Today Completed Goal",
            targetDate = "2026-09-22",
            isCompleted = true,
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        goalDao.insertGoal(todayCompletedGoal)

        // 4. Future Goal (targetDate = 2026-09-25, isCompleted = false) -> PRESERVED
        val futureGoal = DailyGoalEntity(
            id = "goal_future",
            title = "Future Goal",
            targetDate = "2026-09-25",
            isCompleted = false,
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        goalDao.insertGoal(futureGoal)

        // Run cleanup with beforeDate = "2026-09-22" (today)
        val deletedCount = goalDao.cleanupCompletedGoalsBeforeDate("2026-09-22")
        assertEquals(1, deletedCount)

        // Verify:
        // 1. Past completed goal was removed
        assertNull("Past completed goal must be deleted", goalDao.getGoalById("goal_past_comp"))
        // 2. Its subtask was removed
        val subtasksPastComp = goalDao.getSubtasksForGoal("goal_past_comp")
        assertTrue("Subtasks of deleted goal must be removed", subtasksPastComp.isEmpty())

        // 3. Past incomplete goal remains intact
        assertNotNull("Past incomplete goal must be preserved", goalDao.getGoalById("goal_past_incomp"))
        val subtasksPastIncomp = goalDao.getSubtasksForGoal("goal_past_incomp")
        assertEquals("Subtask of preserved goal must remain", 1, subtasksPastIncomp.size)

        // 4. Today completed goal remains intact
        assertNotNull("Today's completed goal must be preserved", goalDao.getGoalById("goal_today_comp"))

        // 5. Future goal remains intact
        assertNotNull("Future goal must be preserved", goalDao.getGoalById("goal_future"))

        // 6. Regular habit and historical habit records are completely untouched
        assertNotNull("Regular habit must be untouched", habitDao.getById("habit_perm"))
        val fetchedRecord = recordDao.getRecord("habit_perm", "2026-09-10")
        assertNotNull("Habit history record must be untouched", fetchedRecord)

        // 7. Repeated cleanup is idempotent and safe
        val secondRunDeletedCount = goalDao.cleanupCompletedGoalsBeforeDate("2026-09-22")
        assertEquals(0, secondRunDeletedCount)
    }

    // --- 8. Daily Reviews ---
    @Test
    fun testDailyReviewOperations() = runBlocking {
        val now = System.currentTimeMillis()
        val review = DailyReviewEntity(
            date = "2026-09-20",
            notes = "Very productive study day.",
            mood = "GREAT",
            createdAt = now,
            updatedAt = now
        )
        reviewDao.upsert(review)

        val fetched = reviewDao.getReview("2026-09-20")
        assertNotNull(fetched)
        assertEquals("Very productive study day.", fetched!!.notes)
        assertEquals("GREAT", fetched.mood)

        // Upsert update
        val updatedReview = review.copy(notes = "Productive and well rested.", updatedAt = now + 100)
        reviewDao.upsert(updatedReview)

        val fetchedUpdated = reviewDao.getReview("2026-09-20")
        assertEquals("Productive and well rested.", fetchedUpdated!!.notes)

        reviewDao.deleteReview("2026-09-20")
        assertNull(reviewDao.getReview("2026-09-20"))
    }
}
