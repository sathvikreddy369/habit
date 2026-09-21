package com.habit1.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundtripIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: com.habit1.app.domain.model.Habit, fromInstant: java.time.Instant, zoneId: java.time.ZoneId) {}
        override fun cancelReminder(habitId: String) {}
    }

    private val fakeNotificationHelper by lazy {
        object : NotificationHelper(ApplicationProvider.getApplicationContext()) {
            override fun areNotificationsEnabled(): Boolean = true
            override fun cancelNotification(habitId: String) {}
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDb = AppDatabase.buildInMemoryDatabase(context)
        targetDb = AppDatabase.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun testFullRoundtrip_exportValidateRestore_assertsFullParity() = runTest(testDispatcher) {
        // 1. Populate source database with diverse entities
        val habits = listOf(
            HabitEntity(
                id = "h_daily",
                name = "Daily Hydration",
                measurementType = "QUANTITY",
                targetValue = 2500.0,
                unit = "ml",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "08:30",
                displayOrder = 0,
                isPaused = false,
                isArchived = false,
                createdAt = 1000L,
                updatedAt = 1000L
            ),
            HabitEntity(
                id = "h_specific",
                name = "Gym Workout",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "SPECIFIC_DAYS",
                scheduleConfig = "{\"days\":[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]}",
                reminderTime = "18:00",
                displayOrder = 1,
                isPaused = false,
                isArchived = false,
                createdAt = 1100L,
                updatedAt = 1100L
            ),
            HabitEntity(
                id = "h_interval",
                name = "Deep Clean Room",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "INTERVAL",
                scheduleConfig = "{\"everyNDays\":3,\"anchorDate\":\"2026-09-01\"}",
                reminderTime = null,
                displayOrder = 2,
                isPaused = false,
                isArchived = false,
                createdAt = 1200L,
                updatedAt = 1200L
            ),
            HabitEntity(
                id = "h_paused",
                name = "Paused Habit",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "10:00",
                displayOrder = 3,
                isPaused = true,
                isArchived = false,
                createdAt = 1300L,
                updatedAt = 1300L
            ),
            HabitEntity(
                id = "h_archived",
                name = "Archived Habit",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = null,
                displayOrder = 4,
                isPaused = false,
                isArchived = true,
                createdAt = 1400L,
                updatedAt = 1400L
            )
        )
        sourceDb.habitDao().insertAll(habits)

        val records = listOf(
            HabitRecordEntity(
                id = "r_1",
                habitId = "h_daily",
                date = "2026-09-20",
                isCompleted = true,
                actualValue = 2500.0,
                targetValue = 2500.0,
                unit = "ml",
                measurementType = "QUANTITY",
                notes = "Drank all bottles",
                recordedAt = 2000L
            ),
            HabitRecordEntity(
                id = "r_2",
                habitId = "h_daily",
                date = "2026-09-21",
                isCompleted = false,
                actualValue = 1500.0,
                targetValue = 2500.0,
                unit = "ml",
                measurementType = "QUANTITY",
                notes = "Busy day",
                recordedAt = 2100L
            ),
            HabitRecordEntity(
                id = "r_3",
                habitId = "h_specific",
                date = "2026-09-21",
                isCompleted = true,
                actualValue = 1.0,
                targetValue = 1.0,
                unit = null,
                measurementType = "BOOLEAN",
                notes = "Leg day",
                recordedAt = 2200L
            )
        )
        sourceDb.habitRecordDao().insertAll(records)

        val goals = listOf(
            DailyGoalEntity(
                id = "g_1",
                targetDate = "2026-09-21",
                title = "Ship Release v1.0",
                notes = "Coordinate release notes",
                isCompleted = false,
                displayOrder = 0,
                createdAt = 3000L,
                updatedAt = 3000L
            )
        )
        sourceDb.dailyGoalDao().insertAllGoals(goals)

        val subtasks = listOf(
            GoalSubtaskEntity(
                id = "s_1",
                goalId = "g_1",
                title = "Verify unit tests",
                isCompleted = true,
                displayOrder = 0,
                createdAt = 3100L
            ),
            GoalSubtaskEntity(
                id = "s_2",
                goalId = "g_1",
                title = "Build release bundle",
                isCompleted = false,
                displayOrder = 1,
                createdAt = 3200L
            )
        )
        sourceDb.dailyGoalDao().insertAllSubtasks(subtasks)

        val reviews = listOf(
            DailyReviewEntity(
                date = "2026-09-21",
                notes = "Great momentum today",
                mood = "ENERGIZED",
                createdAt = 4000L,
                updatedAt = 4000L
            )
        )
        sourceDb.dailyReviewDao().upsertAll(reviews)

        // 2. Export from source database
        val exporter = BackupExporter(sourceDb, "1.0.0")
        val baos = ByteArrayOutputStream()
        val exportSummary = exporter.exportToStream(baos)

        assertEquals(5, exportSummary.habitsCount)
        assertEquals(3, exportSummary.recordsCount)
        assertEquals(1, exportSummary.goalsCount)
        assertEquals(2, exportSummary.subtasksCount)
        assertEquals(1, exportSummary.reviewsCount)
        assertTrue(exportSummary.bytesWritten > 0)

        // 3. Inspect and Validate JSON
        val exportedJson = String(baos.toByteArray(), Charsets.UTF_8)
        val envelope = json.decodeFromString<BackupEnvelopeDto>(exportedJson)

        val validationResult = BackupValidator.validate(envelope)
        assertTrue(validationResult is BackupValidationResult.Valid)

        // 4. Restore into clean target database
        val targetHabitRepo = HabitRepositoryImpl(targetDb.habitDao(), testDispatcher)
        val targetCoordinator = HabitReminderCoordinator(
            habitRepository = targetHabitRepo,
            scheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )
        val importer = BackupImporter(
            database = targetDb,
            reminderCoordinator = targetCoordinator,
            reminderScheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )

        val restoreSummary = importer.restore(envelope, RestoreMode.ReplaceAll)
        assertEquals(5, restoreSummary.habitsRestored)
        assertEquals(3, restoreSummary.recordsRestored)
        assertEquals(1, restoreSummary.goalsRestored)
        assertEquals(2, restoreSummary.subtasksRestored)
        assertEquals(1, restoreSummary.reviewsRestored)

        // 5. Assert 100% field parity
        val restoredHabits = targetDb.habitDao().getAllHabitsList().sortedBy { it.id }
        assertEquals(habits.sortedBy { it.id }, restoredHabits)

        val restoredRecords = targetDb.habitRecordDao().getAllRecordsList().sortedBy { it.id }
        assertEquals(records.sortedBy { it.id }, restoredRecords)

        val restoredGoals = targetDb.dailyGoalDao().getAllGoalsList().sortedBy { it.id }
        assertEquals(goals.sortedBy { it.id }, restoredGoals)

        val restoredSubtasks = targetDb.dailyGoalDao().getAllSubtasksList().sortedBy { it.id }
        assertEquals(subtasks.sortedBy { it.id }, restoredSubtasks)

        val restoredReviews = targetDb.dailyReviewDao().getAllReviews().sortedBy { it.date }
        assertEquals(reviews.sortedBy { it.date }, restoredReviews)
    }
}
