package com.habit1.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit1.app.data.backup.BackupExporter
import com.habit1.app.data.backup.BackupImporter
import com.habit1.app.data.backup.BackupValidator
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.DailyReviewRepository
import com.habit1.app.data.repository.DailyReviewRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import com.habit1.app.ui.history.HistoryUiEvent
import com.habit1.app.ui.history.HistoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Stage 7.6 Physical Device Verification Test.
 * Runs on the actual physical device (e.g. Samsung Galaxy M21 / Android 12)
 * testing real SQLite database, Room migrations, atomic cleanup, History statistics,
 * and backup export/restore.
 */
@RunWith(AndroidJUnit4::class)
class Stage76PhysicalDeviceVerificationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var habitRepo: HabitRepository
    private lateinit var recordRepo: HabitRecordRepository
    private lateinit var goalRepo: DailyGoalRepository
    private lateinit var reviewRepo: DailyReviewRepository

    private val json = Json { ignoreUnknownKeys = true }

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {}
        override fun cancelReminder(habitId: String) {}
    }

    private val fakeNotificationHelper by lazy {
        object : NotificationHelper(context) {
            override fun areNotificationsEnabled(): Boolean = true
            override fun cancelNotification(habitId: String) {}
        }
    }

    private lateinit var coordinator: HabitReminderCoordinator
    private lateinit var importer: BackupImporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepo = HabitRepositoryImpl(database.habitDao(), Dispatchers.IO)
        recordRepo = HabitRecordRepositoryImpl(database.habitRecordDao(), Dispatchers.IO)
        goalRepo = DailyGoalRepositoryImpl(database.dailyGoalDao(), Dispatchers.IO)
        reviewRepo = DailyReviewRepositoryImpl(database.dailyReviewDao(), Dispatchers.IO)

        coordinator = HabitReminderCoordinator(
            habitRepository = habitRepo,
            scheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )
        importer = BackupImporter(
            database = database,
            reminderCoordinator = coordinator,
            reminderScheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun verifyPhysicalDevice_historicalCompletedGoals_cleanupAndHistorySemantics() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Setup Test Dataset: 5/5 on 2026-09-20
        for (i in 1..5) {
            val goal = DailyGoalEntity(
                id = "p_g_5_$i",
                title = "5/5 Goal $i",
                targetDate = "2026-09-20",
                isCompleted = true,
                displayOrder = i,
                notes = null,
                createdAt = now,
                updatedAt = now
            )
            goalRepo.createGoal(goal)
            goalRepo.addSubtask(GoalSubtaskEntity("p_s_5_$i", "p_g_5_$i", "Sub $i", true, 0, now))
        }

        // 2. Setup Test Dataset: 3/4 on 2026-09-21 (3 completed, 1 incomplete)
        for (i in 1..3) {
            val goal = DailyGoalEntity(
                id = "p_g_34_$i",
                title = "3/4 Completed $i",
                targetDate = "2026-09-21",
                isCompleted = true,
                displayOrder = i,
                notes = null,
                createdAt = now,
                updatedAt = now
            )
            goalRepo.createGoal(goal)
            goalRepo.addSubtask(GoalSubtaskEntity("p_s_34_$i", "p_g_34_$i", "Sub $i", true, 0, now))
        }
        val incompGoal = DailyGoalEntity(
            id = "p_g_34_inc",
            title = "3/4 Incomplete",
            targetDate = "2026-09-21",
            isCompleted = false,
            displayOrder = 4,
            notes = null,
            createdAt = now,
            updatedAt = now
        )
        goalRepo.createGoal(incompGoal)
        goalRepo.addSubtask(GoalSubtaskEntity("p_s_34_inc", "p_g_34_inc", "Sub Incomp", false, 0, now))

        // 3. Setup Test Dataset: 0/1 on 2026-09-22 (0 completed, 1 incomplete)
        val zeroGoal = DailyGoalEntity(
            id = "p_g_01",
            title = "0/1 Goal",
            targetDate = "2026-09-22",
            isCompleted = false,
            displayOrder = 0,
            notes = null,
            createdAt = now,
            updatedAt = now
        )
        goalRepo.createGoal(zeroGoal)

        // 4. Setup Test Dataset: Current day goals on 2026-09-23
        val currentGoal1 = DailyGoalEntity("p_g_cur_1", "Today Done", "2026-09-23", true, 0, null, now, now)
        val currentGoal2 = DailyGoalEntity("p_g_cur_2", "Today Pending", "2026-09-23", false, 1, null, now, now)
        goalRepo.createGoal(currentGoal1)
        goalRepo.createGoal(currentGoal2)

        // 5. Setup Regular Habit on device
        val habit = HabitEntity(
            id = "p_habit_1",
            name = "Device Habit",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        habitRepo.createHabit(habit)
        recordRepo.recordProgress(
            HabitRecordEntity("p_rec_1", "p_habit_1", "2026-09-20", 1.0, 1.0, "BOOLEAN", null, true, null, now)
        )

        // Verify initial state before cleanup
        assertEquals(5, goalRepo.getGoalsForDate("2026-09-20").size)
        assertEquals(4, goalRepo.getGoalsForDate("2026-09-21").size)
        assertEquals(1, goalRepo.getGoalsForDate("2026-09-22").size)
        assertEquals(2, goalRepo.getGoalsForDate("2026-09-23").size)

        // 6. Execute atomic cleanup with cutoff = 2026-09-23 (today)
        val deletedCount = goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-23")
        // 5 from Sep 20 + 3 from Sep 21 = 8 deleted completed goals
        assertEquals(8, deletedCount)

        // 7. Verify atomic results on physical device SQLite:
        // Sep 20 (5/5): All detailed goals and subtasks deleted, aggregate is 5/5
        assertTrue(goalRepo.getGoalsForDate("2026-09-20").isEmpty())
        for (i in 1..5) {
            assertTrue(database.dailyGoalDao().getSubtasksForGoal("p_g_5_$i").isEmpty())
        }
        val aggSep20 = goalRepo.getAggregateForDate("2026-09-20")
        assertNotNull(aggSep20)
        assertEquals(5, aggSep20!!.completedCount)
        assertEquals(5, aggSep20.totalCount)

        // Sep 21 (3/4): 3 completed goals deleted, 1 incomplete retained with its subtask. Aggregate is 3/4
        val remainingSep21 = goalRepo.getGoalsForDate("2026-09-21")
        assertEquals(1, remainingSep21.size)
        assertEquals("p_g_34_inc", remainingSep21[0].goal.id)
        assertFalse(remainingSep21[0].goal.isCompleted)
        assertEquals(1, remainingSep21[0].subtasks.size)
        val aggSep21 = goalRepo.getAggregateForDate("2026-09-21")
        assertNotNull(aggSep21)
        assertEquals(3, aggSep21!!.completedCount)
        assertEquals(4, aggSep21.totalCount)

        // Sep 22 (0/1): 0 completed goals deleted. Incomplete goal retained. No aggregate created.
        assertEquals(1, goalRepo.getGoalsForDate("2026-09-22").size)
        assertNull(goalRepo.getAggregateForDate("2026-09-22"))

        // Sep 23 (Today): Current goals retained untouched
        assertEquals(2, goalRepo.getGoalsForDate("2026-09-23").size)

        // Regular Habit untouched
        assertNotNull(habitRepo.getHabitById("p_habit_1"))
        assertNotNull(recordRepo.getRecord("p_habit_1", "2026-09-20"))

        // 8. Second cleanup run is safe & idempotent
        val secondDeleted = goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-23")
        assertEquals(0, secondDeleted)
        assertEquals(3, goalRepo.getAggregateForDate("2026-09-21")!!.completedCount)
        assertEquals(4, goalRepo.getAggregateForDate("2026-09-21")!!.totalCount)

        // 9. History Integration Verification
        val scope = CoroutineScope(Dispatchers.IO)
        val historyViewModel = HistoryViewModel(
            habitRepository = habitRepo,
            habitRecordRepository = recordRepo,
            dailyGoalRepository = goalRepo,
            dailyReviewRepository = reviewRepo,
            zoneId = ZoneId.of("UTC"),
            coroutineScope = scope
        )

        val state = historyViewModel.uiState.first { !it.isLoading }

        // Check Sep 20: 5/5, 100%, activity recorded
        val day20 = state.calendarDays.find { it.date == LocalDate.parse("2026-09-20") }
        assertNotNull(day20)
        assertEquals(5, day20!!.completedGoalsCount)
        assertEquals(5, day20.totalGoalsCount)
        assertTrue(day20.hasRecordedActivity)

        // Check Sep 21: 3/4, 75%, activity recorded (no double counting!)
        val day21 = state.calendarDays.find { it.date == LocalDate.parse("2026-09-21") }
        assertNotNull(day21)
        assertEquals(3, day21!!.completedGoalsCount)
        assertEquals(4, day21.totalGoalsCount)
        assertTrue(day21.hasRecordedActivity)

        // Check Sep 22: 0/1, 0%
        val day22 = state.calendarDays.find { it.date == LocalDate.parse("2026-09-22") }
        assertNotNull(day22)
        assertEquals(0, day22!!.completedGoalsCount)
        assertEquals(1, day22.totalGoalsCount)

        // Check Sep 23: 1/2 (current day)
        val day23 = state.calendarDays.find { it.date == LocalDate.parse("2026-09-23") }
        assertNotNull(day23)
        assertEquals(1, day23!!.completedGoalsCount)
        assertEquals(2, day23.totalGoalsCount)

        // Month summary: 5 + 3 + 0 + 1 = 9 completed / 5 + 4 + 1 + 2 = 12 total goals = 75%
        val summary = state.monthSummary
        assertNotNull(summary)
        assertEquals(9, summary!!.completedGoals)
        assertEquals(12, summary.totalGoals)
        assertEquals(75.0f, summary.goalCompletionRate, 0.1f)

        // Yearly overview for September 2026 matches exact totals without double-counting
        val yearSep = state.yearlyOverview.find { it.yearMonth == YearMonth.of(2026, 9) }
        assertNotNull(yearSep)
        assertEquals(9, yearSep!!.completedGoals)
        assertEquals(12, yearSep.totalGoals)
        assertEquals(75.0f, yearSep.goalCompletionRate, 0.1f)

        // Day breakdown for Sep 20 (aggregate-only day)
        historyViewModel.onEvent(HistoryUiEvent.SelectDate(LocalDate.parse("2026-09-20")))
        val updatedState20 = historyViewModel.uiState.first { it.selectedDateBreakdown?.date == LocalDate.parse("2026-09-20") }
        val breakdown20 = updatedState20.selectedDateBreakdown
        assertNotNull(breakdown20)
        assertNotNull(breakdown20!!.historicalGoalAggregate)
        assertEquals(5, breakdown20.historicalGoalAggregate!!.completedCount)
        assertEquals(5, breakdown20.historicalGoalAggregate!!.totalCount)
        assertTrue(breakdown20.goals.isEmpty())

        // Day breakdown for Sep 21 (aggregate + retained incomplete day)
        historyViewModel.onEvent(HistoryUiEvent.SelectDate(LocalDate.parse("2026-09-21")))
        val updatedState21 = historyViewModel.uiState.first { it.selectedDateBreakdown?.date == LocalDate.parse("2026-09-21") }
        val breakdown21 = updatedState21.selectedDateBreakdown
        assertNotNull(breakdown21)
        assertNotNull(breakdown21!!.historicalGoalAggregate)
        assertEquals(3, breakdown21.historicalGoalAggregate!!.completedCount)
        assertEquals(4, breakdown21.historicalGoalAggregate!!.totalCount)
        assertEquals(1, breakdown21.goals.size)
        assertEquals("p_g_34_inc", breakdown21.goals[0].id)
        assertFalse(breakdown21.goals[0].isCompleted)

        // 10. Backup Export and Restore on Device
        val exporter = BackupExporter(database, "1.0.0")
        val outStream = ByteArrayOutputStream()
        val exportSummary = exporter.exportToStream(outStream)
        assertEquals(2, exportSummary.aggregatesCount)

        val jsonString = outStream.toString(Charsets.UTF_8.name())
        val envelope = json.decodeFromString(BackupEnvelopeDto.serializer(), jsonString)

        val validation = BackupValidator.validate(envelope)
        assertTrue(validation is com.habit1.app.data.backup.BackupValidationResult.Valid)

        // Restore ReplaceAll
        val replaceSummary = importer.restore(envelope, RestoreMode.ReplaceAll)
        assertEquals(2, replaceSummary.aggregatesRestored)

        assertEquals(5, goalRepo.getAggregateForDate("2026-09-20")!!.completedCount)
        assertEquals(3, goalRepo.getAggregateForDate("2026-09-21")!!.completedCount)

        // Restore Merge
        val mergeSummary = importer.restore(envelope, RestoreMode.Merge)
        assertEquals(2, mergeSummary.aggregatesRestored)
        assertEquals(2, database.dailyGoalDao().getAllAggregates().size)
    }
}
