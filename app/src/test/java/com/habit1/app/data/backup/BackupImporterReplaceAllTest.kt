package com.habit1.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.backup.model.HabitBackupDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
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
import org.junit.Assert.assertNull
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
class BackupImporterReplaceAllTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private val scheduledHabits = mutableListOf<String>()
    private val canceledHabits = mutableListOf<String>()

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {
            scheduledHabits.add(habit.id)
        }

        override fun cancelReminder(habitId: String) {
            canceledHabits.add(habitId)
        }
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
        canceledHabits.clear()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testReplaceAll_cancelsOldAlarms_wipesOldData_schedulesNewAlarms() = runTest(testDispatcher) {
        // 1. Pre-populate database with habits A and B
        val habitA = HabitEntity(
            id = "habit_A",
            name = "Habit A",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "08:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val habitB = HabitEntity(
            id = "habit_B",
            name = "Habit B",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "09:00",
            displayOrder = 1,
            isPaused = false,
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.habitDao().insert(habitA)
        database.habitDao().insert(habitB)

        // 2. Prepare backup containing only Habit C
        val payload = BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "habit_C",
                    name = "Habit C",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "07:00",
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 2000L,
                    updatedAt = 2000L
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

        // 3. Execute Replace All
        val summary = importer.restore(envelope, RestoreMode.ReplaceAll)

        assertEquals(1, summary.habitsRestored)

        // Verify pre-wipe cancellation of old alarms
        assertTrue(canceledHabits.contains("habit_A"))
        assertTrue(canceledHabits.contains("habit_B"))

        // Verify database content: A and B are wiped, C is present
        assertNull(database.habitDao().getById("habit_A"))
        assertNull(database.habitDao().getById("habit_B"))
        assertNotNull(database.habitDao().getById("habit_C"))

        // Verify newly restored habit C alarm is scheduled
        assertTrue(scheduledHabits.contains("habit_C"))
    }

    @Test
    fun testReplaceAll_transactionFailure_rollsBackDataAndRecoversOriginalAlarms() = runTest(testDispatcher) {
        val habitA = HabitEntity(
            id = "habit_A",
            name = "Habit A",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "08:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.habitDao().insert(habitA)

        // Create payload with duplicate habit IDs that will fail SQLite unique constraint on insert
        val payloadWithDuplicate = BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "dup_id",
                    name = "First",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "07:00",
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 2000L,
                    updatedAt = 2000L
                ),
                HabitBackupDto(
                    id = "dup_id",
                    name = "Second",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "07:00",
                    displayOrder = 1,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 2000L,
                    updatedAt = 2000L
                )
            )
        )
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-21T12:00:00Z",
            checksum = "dummy",
            payload = payloadWithDuplicate
        )

        var failureOccurred = false
        try {
            importer.restore(envelope, RestoreMode.ReplaceAll)
        } catch (t: Throwable) {
            failureOccurred = true
        }

        assertTrue(failureOccurred)

        // Verify original data is intact due to Room transaction rollback
        assertNotNull(database.habitDao().getById("habit_A"))

        // Verify alarms were recovered by coordinator on failure
        assertTrue(scheduledHabits.contains("habit_A"))
    }
}
