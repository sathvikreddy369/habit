package com.habit1.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.backup.model.HabitBackupDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.BackupRepositoryImpl
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdversarialRestoreIntegrityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var backupRepository: BackupRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true }

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {}
        override fun cancelReminder(habitId: String) {}
    }

    private val fakeNotificationHelper by lazy {
        object : NotificationHelper(ApplicationProvider.getApplicationContext()) {
            override fun areNotificationsEnabled(): Boolean = true
            override fun cancelNotification(habitId: String) {}
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.buildInMemoryDatabase(context)
        val habitRepo = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        val coordinator = HabitReminderCoordinator(
            habitRepository = habitRepo,
            scheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )
        val exporter = BackupExporter(database, "1.0.0")
        val importer = BackupImporter(
            database = database,
            reminderCoordinator = coordinator,
            reminderScheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )
        backupRepository = BackupRepositoryImpl(
            context = context,
            database = database,
            exporter = exporter,
            importer = importer,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedExistingData() {
        val habit = HabitEntity(
            id = "existing_habit_1",
            name = "Existing Morning Run",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "07:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.habitDao().insert(habit)

        val record = HabitRecordEntity(
            id = "existing_rec_1",
            habitId = "existing_habit_1",
            date = "2026-09-21",
            isCompleted = true,
            actualValue = 1.0,
            targetValue = 1.0,
            unit = null,
            measurementType = "BOOLEAN",
            notes = "Good run",
            recordedAt = 1100L
        )
        database.habitRecordDao().upsert(record)

        val goal = DailyGoalEntity(
            id = "existing_goal_1",
            targetDate = "2026-09-21",
            title = "Existing Daily Goal",
            notes = "Must stay untouched",
            isCompleted = false,
            displayOrder = 0,
            createdAt = 1200L,
            updatedAt = 1200L
        )
        database.dailyGoalDao().insertGoal(goal)

        val subtask = GoalSubtaskEntity(
            id = "existing_subtask_1",
            goalId = "existing_goal_1",
            title = "Subtask 1",
            isCompleted = false,
            displayOrder = 0,
            createdAt = 1300L
        )
        database.dailyGoalDao().insertSubtask(subtask)
    }

    private suspend fun assertExistingDataUntouched() {
        val habit = database.habitDao().getById("existing_habit_1")
        assertNotNull("Existing habit must remain intact", habit)
        assertEquals("Existing Morning Run", habit?.name)

        val record = database.habitRecordDao().getRecord("existing_habit_1", "2026-09-21")
        assertNotNull("Existing record must remain intact", record)
        assertEquals("Good run", record?.notes)

        val goal = database.dailyGoalDao().getGoalWithSubtasksById("existing_goal_1")
        assertNotNull("Existing goal must remain intact", goal)
        assertEquals("Existing Daily Goal", goal?.goal?.title)
        assertEquals(1, goal?.subtasks?.size)
        assertEquals("Subtask 1", goal?.subtasks?.first()?.title)
    }

    private fun writeTempBackupFile(content: String): Uri {
        val file = File(context.cacheDir, "test_backup_${System.nanoTime()}.json")
        file.writeText(content, Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    @Test
    fun testValidBackup_replaceSuccessful() = runTest(testDispatcher) {
        seedExistingData()

        val newPayload = BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "new_habit_1",
                    name = "New Evening Walk",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "20:00",
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 2000L,
                    updatedAt = 2000L
                )
            )
        )
        val checksum = BackupChecksumCalculator.computeChecksum(newPayload)
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-23T12:00:00Z",
            checksum = checksum,
            payload = newPayload
        )
        val uri = writeTempBackupFile(json.encodeToString(envelope))

        val result = backupRepository.restoreBackup(uri, RestoreMode.ReplaceAll)
        assertTrue(result.isSuccess)
        val summary = result.getOrNull()
        assertNotNull(summary)
        assertEquals(1, summary?.habitsRestored)

        // New habit exists, old habit wiped
        assertNotNull(database.habitDao().getById("new_habit_1"))
        assertEquals(null, database.habitDao().getById("existing_habit_1"))
    }

    @Test
    fun testCorruptJson_failsSafely_andPreservesExistingData() = runTest(testDispatcher) {
        seedExistingData()

        val corruptJson = "{\"formatVersion\": 1, \"appVersion\": \"1.0.0\", \"payload\": {CORRUPT_INVALID_SYNTAX...}"
        val uri = writeTempBackupFile(corruptJson)

        val result = backupRepository.restoreBackup(uri, RestoreMode.ReplaceAll)
        assertTrue("Restore must fail on corrupt JSON", result.isFailure)

        // Invariant: User's existing database must remain unchanged
        assertExistingDataUntouched()
    }

    @Test
    fun testTruncatedJson_failsSafely_andPreservesExistingData() = runTest(testDispatcher) {
        seedExistingData()

        val truncatedJson = "{\"formatVersion\": 1, \"appVersion\": \"1.0.0\", \"exportedAt\": \"2026-09-23T12:00:00Z\", \"checksum\": \"abc\", \"payload\": {\"habits\": [{\"id\": \"h1"
        val uri = writeTempBackupFile(truncatedJson)

        val result = backupRepository.restoreBackup(uri, RestoreMode.ReplaceAll)
        assertTrue("Restore must fail on truncated JSON", result.isFailure)

        // Invariant: User's existing database must remain unchanged
        assertExistingDataUntouched()
    }

    @Test
    fun testChecksumMismatch_failsValidation_andPreservesExistingData() = runTest(testDispatcher) {
        seedExistingData()

        val payload = BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "tampered_habit",
                    name = "Tampered Habit",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "10:00",
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 3000L,
                    updatedAt = 3000L
                )
            )
        )
        // Set bad checksum
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-23T12:00:00Z",
            checksum = "0000000000000000000000000000000000000000000000000000000000000000",
            payload = payload
        )
        val uri = writeTempBackupFile(json.encodeToString(envelope))

        val result = backupRepository.restoreBackup(uri, RestoreMode.ReplaceAll)
        assertTrue("Restore must fail on checksum mismatch", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("checksum", ignoreCase = true) == true)

        // Invariant: User's existing database must remain unchanged
        assertExistingDataUntouched()
    }

    @Test
    fun testUnsupportedVersion_failsValidation_andPreservesExistingData() = runTest(testDispatcher) {
        seedExistingData()

        val payload = BackupPayloadDto()
        val envelope = BackupEnvelopeDto(
            formatVersion = 999,
            appVersion = "9.9.9",
            exportedAt = "2026-09-23T12:00:00Z",
            checksum = BackupChecksumCalculator.computeChecksum(payload),
            payload = payload
        )
        val uri = writeTempBackupFile(json.encodeToString(envelope))

        val result = backupRepository.restoreBackup(uri, RestoreMode.ReplaceAll)
        assertTrue("Restore must fail on unsupported format version", result.isFailure)

        // Invariant: User's existing database must remain unchanged
        assertExistingDataUntouched()
    }

    @Test
    fun testIntegrityOrConstraintFailure_rollsBackTransaction_andPreservesExistingData() = runTest(testDispatcher) {
        seedExistingData()

        // Payload with duplicate IDs causing SQLite primary key constraint failure during insert
        val payloadWithDuplicateHabit = BackupPayloadDto(
            habits = listOf(
                HabitBackupDto(
                    id = "conflict_id",
                    name = "Habit 1",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "08:00",
                    displayOrder = 0,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 4000L,
                    updatedAt = 4000L
                ),
                HabitBackupDto(
                    id = "conflict_id",
                    name = "Habit 2",
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = "09:00",
                    displayOrder = 1,
                    isPaused = false,
                    isArchived = false,
                    createdAt = 4100L,
                    updatedAt = 4100L
                )
            )
        )
        val checksum = BackupChecksumCalculator.computeChecksum(payloadWithDuplicateHabit)
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-23T12:00:00Z",
            checksum = checksum,
            payload = payloadWithDuplicateHabit
        )
        val uri = writeTempBackupFile(json.encodeToString(envelope))

        val result = backupRepository.restoreBackup(uri, RestoreMode.ReplaceAll)
        assertTrue("Restore must fail on SQLite constraint conflict", result.isFailure)

        // Invariant: Transaction rollback must keep existing database 100% intact
        assertExistingDataUntouched()
    }
}
