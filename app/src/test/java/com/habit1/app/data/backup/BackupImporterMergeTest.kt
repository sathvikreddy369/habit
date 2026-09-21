package com.habit1.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.backup.model.DailyGoalBackupDto
import com.habit1.app.data.backup.model.GoalSubtaskBackupDto
import com.habit1.app.data.backup.model.HabitBackupDto
import com.habit1.app.data.backup.model.HabitRecordBackupDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupImporterMergeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private val scheduledHabits = mutableListOf<String>()

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {
            scheduledHabits.add(habit.id)
        }

        override fun cancelReminder(habitId: String) {}
    }

    private val fakeNotificationHelper by lazy {
        object : NotificationHelper(ApplicationProvider.getApplicationContext()) {
            override fun areNotificationsEnabled(): Boolean = true
            override fun cancelNotification(habitId: String) {}
        }
    }

    private lateinit var coordinator: HabitReminderCoordinator
    private lateinit var importer: BackupImporter

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        val habitRepo = HabitRepositoryImpl(database.habitDao(), testDispatcher)

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

        scheduledHabits.clear()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testMerge_habitTimestamps_newerUpdates_olderPreserves() = runTest(testDispatcher) {
        // Pre-populate with Habit 1 (updatedAt = 1000L) and Habit 2 (updatedAt = 3000L)
        database.habitDao().insert(
            HabitEntity(
                id = "h1",
                name = "Old Local Name",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = null,
                displayOrder = 0,
                isPaused = false,
                isArchived = false,
                createdAt = 500L,
                updatedAt = 1000L
            )
        )
        database.habitDao().insert(
            HabitEntity(
                id = "h2",
                name = "Current Local Name",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = null,
                displayOrder = 1,
                isPaused = false,
                isArchived = false,
                createdAt = 500L,
                updatedAt = 3000L
            )
        )

        // Backup has h1 newer (2000L), h2 older (2000L), and h3 completely new
        val payload = BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "h1",
                    name = "Updated Backup Name",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = null,
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 500L,
                    updatedAt = 2000L // newer than 1000L -> updates
                ),
                HabitBackupDto(
                    id = "h2",
                    name = "Stale Backup Name",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = null,
                    displayOrder = 1,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 500L,
                    updatedAt = 2000L // older than 3000L -> ignored
                ),
                HabitBackupDto(
                    id = "h3",
                    name = "New Habit Name",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "09:00",
                    displayOrder = 2,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 1500L,
                    updatedAt = 1500L // new -> inserted
                )
            )
        )
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-21T12:00:00Z",
            checksum = BackupChecksumCalculator.computeChecksum(payload),
            payload = payload
        )

        importer.restore(envelope, RestoreMode.Merge)

        val updatedH1 = database.habitDao().getById("h1")
        assertEquals("Updated Backup Name", updatedH1?.name)

        val preservedH2 = database.habitDao().getById("h2")
        assertEquals("Current Local Name", preservedH2?.name)

        val insertedH3 = database.habitDao().getById("h3")
        assertEquals("New Habit Name", insertedH3?.name)
    }

    @Test
    fun testMerge_historicalRecords_preservesConflictingLocalRecord() = runTest(testDispatcher) {
        val habit = HabitEntity(
            id = "h1",
            name = "Pushups",
            measurementType = "NUMERIC",
            targetValue = 50.0,
            unit = "reps",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = null,
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = 500L,
            updatedAt = 500L
        )
        database.habitDao().insert(habit)

        // Existing local record: Sep 20 completed 50 reps
        val localRecord = HabitRecordEntity(
            id = "r_local",
            habitId = "h1",
            date = "2026-09-20",
            isCompleted = true,
            actualValue = 50.0,
            targetValue = 50.0,
            unit = "reps",
            measurementType = "NUMERIC",
            notes = "Local factual record",
            recordedAt = 1000L
        )
        database.habitRecordDao().upsert(localRecord)

        // Backup has conflicting record for same habit & date (only 20 reps, incomplete)
        // and a new record for Sep 21
        val payload = BackupPayloadDto(
            records = listOf(
                HabitRecordBackupDto(
                    id = "r_backup",
                    habitId = "h1",
                    date = "2026-09-20",
                    isCompleted = false,
                    actualValue = 20.0,
                    targetValue = 50.0,
                    unit = "reps",
                    measurementType = "NUMERIC",
                    notes = "Backup conflicting record",
                    recordedAt = 2000L
                ),
                HabitRecordBackupDto(
                    id = "r_new",
                    habitId = "h1",
                    date = "2026-09-21",
                    isCompleted = true,
                    actualValue = 50.0,
                    targetValue = 50.0,
                    unit = "reps",
                    measurementType = "NUMERIC",
                    notes = "New record",
                    recordedAt = 2000L
                )
            )
        )
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-21T12:00:00Z",
            checksum = BackupChecksumCalculator.computeChecksum(payload),
            payload = payload
        )

        val summary = importer.restore(envelope, RestoreMode.Merge)

        assertEquals(1, summary.conflictingRecordsPreserved)

        // Factual history rule: Sep 20 local record must NOT be overwritten!
        val recordSep20 = database.habitRecordDao().getRecord("h1", "2026-09-20")
        assertNotNull(recordSep20)
        assertEquals(50.0, recordSep20?.actualValue)
        assertTrue(recordSep20?.isCompleted == true)
        assertEquals("Local factual record", recordSep20?.notes)

        // Sep 21 new record is inserted
        val recordSep21 = database.habitRecordDao().getRecord("h1", "2026-09-21")
        assertNotNull(recordSep21)
        assertEquals("New record", recordSep21?.notes)
    }

    @Test
    fun testMerge_goalSubtasks_preservesLocalState() = runTest(testDispatcher) {
        val goal = DailyGoalEntity(
            id = "g1",
            targetDate = "2026-09-21",
            title = "Project Launch",
            notes = null,
            isCompleted = false,
            displayOrder = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.dailyGoalDao().insertGoal(goal)

        val localSubtask = GoalSubtaskEntity(
            id = "s1",
            goalId = "g1",
            title = "Local Subtask Title",
            isCompleted = false,
            displayOrder = 0,
            createdAt = 1000L
        )
        database.dailyGoalDao().insertSubtask(localSubtask)

        // Backup has subtask s1 with different completion state and a new subtask s2
        val payload = BackupPayloadDto(
            goals = listOf(
                DailyGoalBackupDto(
                    id = "g1",
                    targetDate = "2026-09-21",
                    title = "Project Launch",
                    notes = null,
                    isCompleted = false,
                    displayOrder = 0,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            ),
            subtasks = listOf(
                GoalSubtaskBackupDto(
                    id = "s1",
                    goalId = "g1",
                    title = "Backup Subtask Title",
                    isCompleted = true,
                    displayOrder = 0,
                    createdAt = 1000L
                ),
                GoalSubtaskBackupDto(
                    id = "s2",
                    goalId = "g1",
                    title = "New Subtask",
                    isCompleted = false,
                    displayOrder = 1,
                    createdAt = 1500L
                )
            )
        )
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-21T12:00:00Z",
            checksum = BackupChecksumCalculator.computeChecksum(payload),
            payload = payload
        )

        importer.restore(envelope, RestoreMode.Merge)

        val subtasks = database.dailyGoalDao().getSubtasksForGoal("g1")
        assertEquals(2, subtasks.size)

        val subtaskS1 = subtasks.find { it.id == "s1" }
        // Subtasks lack updatedAt: Current local state is preserved!
        assertEquals("Local Subtask Title", subtaskS1?.title)
        assertEquals(false, subtaskS1?.isCompleted)

        val subtaskS2 = subtasks.find { it.id == "s2" }
        assertNotNull(subtaskS2)
        assertEquals("New Subtask", subtaskS2?.title)
    }
}
