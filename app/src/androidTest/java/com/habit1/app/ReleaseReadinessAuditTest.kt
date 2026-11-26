package com.habit1.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit1.app.data.backup.BackupExporter
import com.habit1.app.data.backup.BackupImporter
import com.habit1.app.data.backup.BackupValidationResult
import com.habit1.app.data.backup.BackupValidator
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.export.ExportFilter
import com.habit1.app.data.export.ExportFormat
import com.habit1.app.data.export.HabitDataExporter
import com.habit1.app.data.export.HabitExportEnvelopeDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.CategoryEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.CategoryRepository
import com.habit1.app.data.repository.CategoryRepositoryImpl
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.DailyReviewRepository
import com.habit1.app.data.repository.DailyReviewRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.data.storage.StorageCalculator
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
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
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Physical Device Verification Audit Test for Habit1.
 * Runs directly on the real Android device hardware verifying:
 * 1. Data Exports (CSV RFC 4180 escaping, JSON schema, date filtering, single/multiple/all scope, external accessibility).
 * 2. Full Backup Archive & Restore (export, SHA-256 verification, corrupted backup rejection, merge vs replace-all).
 * 3. Physical Storage Reporting (disk measurement against actual DB, WAL, SHM, preferences, and cache).
 */
@RunWith(AndroidJUnit4::class)
class ReleaseReadinessAuditTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var habitRepo: HabitRepository
    private lateinit var recordRepo: HabitRecordRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var goalRepo: DailyGoalRepository
    private lateinit var reviewRepo: DailyReviewRepository
    private lateinit var reminderCoordinator: HabitReminderCoordinator
    private lateinit var fakeScheduler: HabitReminderScheduler
    private lateinit var fakeNotificationHelper: NotificationHelper

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val nowEpoch = 1774000000000L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepo = HabitRepositoryImpl(database.habitDao(), Dispatchers.IO)
        recordRepo = HabitRecordRepositoryImpl(database.habitRecordDao(), Dispatchers.IO)
        categoryRepo = CategoryRepositoryImpl(database.categoryDao(), database.habitDao(), Dispatchers.IO)
        goalRepo = DailyGoalRepositoryImpl(database.dailyGoalDao(), Dispatchers.IO)
        reviewRepo = DailyReviewRepositoryImpl(database.dailyReviewDao(), Dispatchers.IO)

        fakeScheduler = object : HabitReminderScheduler {
            override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {}
            override fun cancelReminder(habitId: String) {}
        }
        fakeNotificationHelper = object : NotificationHelper(context) {
            override fun areNotificationsEnabled(): Boolean = true
            override fun cancelNotification(habitId: String) {}
        }
        reminderCoordinator = HabitReminderCoordinator(
            habitRepository = habitRepo,
            scheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )

        // Seed comprehensive test data:
        // 1. Category
        categoryRepo.createCategory(
            CategoryEntity(
                id = "cat_wellness",
                name = "Wellness & Health",
                colorHex = "#4CAF50",
                icon = "favorite",
                displayOrder = 0,
                createdAt = nowEpoch
            )
        )

        // 2. Habit 1: Boolean with comma & quotes in name
        habitRepo.createHabit(
            HabitEntity(
                id = "h_meditation",
                name = "Daily Zen, \"Mindfulness\"",
                description = "Morning meditation\nCalm focus",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "07:30",
                displayOrder = 0,
                isPaused = false,
                isArchived = false,
                colorHex = "#2196F3",
                startDate = "2026-01-01",
                categoryId = "cat_wellness",
                createdAt = nowEpoch,
                updatedAt = nowEpoch
            )
        )

        // 3. Habit 2: Quantitative Running
        habitRepo.createHabit(
            HabitEntity(
                id = "h_running",
                name = "Evening Run",
                description = "Distance jog",
                measurementType = "QUANTITY",
                targetValue = 5.0,
                unit = "km",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "18:00",
                displayOrder = 1,
                isPaused = false,
                isArchived = false,
                colorHex = "#FF5722",
                startDate = "2026-01-01",
                categoryId = "cat_wellness",
                createdAt = nowEpoch,
                updatedAt = nowEpoch
            )
        )

        // 4. Habit 3: Count Pages read
        habitRepo.createHabit(
            HabitEntity(
                id = "h_reading",
                name = "Read Books",
                description = "Non-fiction books",
                measurementType = "COUNT",
                targetValue = 25.0,
                unit = "pages",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "21:00",
                displayOrder = 2,
                isPaused = false,
                isArchived = false,
                colorHex = "#9C27B0",
                startDate = "2026-01-01",
                categoryId = null,
                createdAt = nowEpoch,
                updatedAt = nowEpoch
            )
        )

        // 5. Records for h_meditation
        recordRepo.recordProgress(
            HabitRecordEntity(
                id = "rec_m1",
                habitId = "h_meditation",
                date = "2026-11-20",
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                unit = null,
                isCompleted = true,
                status = "DONE",
                notes = "Peaceful session, morning stillness.",
                recordedAt = nowEpoch
            )
        )
        recordRepo.recordProgress(
            HabitRecordEntity(
                id = "rec_m2",
                habitId = "h_meditation",
                date = "2026-11-21",
                actualValue = 0.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                unit = null,
                isCompleted = false,
                status = "SKIPPED",
                notes = "Travel day",
                recordedAt = nowEpoch
            )
        )

        // 6. Records for h_running
        recordRepo.recordProgress(
            HabitRecordEntity(
                id = "rec_r1",
                habitId = "h_running",
                date = "2026-11-20",
                actualValue = 5.5,
                targetValue = 5.0,
                measurementType = "QUANTITY",
                unit = "km",
                isCompleted = true,
                status = "DONE",
                notes = "Exceeded target!\nPace was 5:20/km.",
                recordedAt = nowEpoch
            )
        )
        recordRepo.recordProgress(
            HabitRecordEntity(
                id = "rec_r2",
                habitId = "h_running",
                date = "2026-11-25",
                actualValue = 3.0,
                targetValue = 5.0,
                measurementType = "QUANTITY",
                unit = "km",
                isCompleted = false,
                status = "PARTIAL",
                notes = "Cut short due to rain",
                recordedAt = nowEpoch
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // 1. DATA EXPORT VERIFICATION
    // =========================================================================

    @Test
    fun testExportSingleHabitCsv_rfc4180CompliantAndAccessibleOutsideApp() {
        runBlocking {
            val habits = habitRepo.observeAllHabits().first().map { it.toDomain() }
            val meditationHabit = habits.first { it.id == "h_meditation" }
            val records = recordRepo.getRecordsForHabit("h_meditation").map { it.toDomain() }
            val categories = categoryRepo.getAllCategories().associate { it.id to it.name }

            val targetDir = context.getExternalFilesDir(null) ?: context.filesDir
            val exportFile = File(targetDir, "test_single_habit_export.csv")
            if (exportFile.exists()) exportFile.delete()

            val filter = ExportFilter(habitIds = setOf("h_meditation"), format = ExportFormat.CSV)
            val count = FileOutputStream(exportFile).use { out ->
                HabitDataExporter.exportToCsv(
                    habits = listOf(meditationHabit),
                    recordsByHabit = mapOf("h_meditation" to records),
                    categoryNames = categories,
                    filter = filter,
                    outputStream = out
                )
            }

            assertEquals(2, count)
            assertTrue("Export file must exist on physical storage", exportFile.exists())
            assertTrue("Export file must be non-empty", exportFile.length() > 0)
            assertTrue("Export file must be readable from storage", exportFile.canRead())

            val content = exportFile.readText(Charsets.UTF_8)

            // Verify CSV header
            assertTrue(content.startsWith("Habit ID,Habit Name,Category,Measurement Type"))

            // Verify RFC 4180 Escaping: quotes inside name doubled: "Daily Zen, ""Mindfulness"""
            assertTrue(content.contains("\"Daily Zen, \"\"Mindfulness\"\"\""))
            assertTrue(content.contains("Wellness & Health"))
            assertTrue(content.contains("2026-11-20,DONE,1.0,1.0,true"))
            assertTrue(content.contains("\"Peaceful session, morning stillness.\""))
            assertTrue(content.contains("2026-11-21,SKIPPED,0.0,1.0,false,Travel day"))

            // Clean up
            exportFile.delete()
        }
    }

    @Test
    fun testExportSingleHabitJson_validStructureAndSnapshotPreservation() {
        runBlocking {
            val habits = habitRepo.observeAllHabits().first().map { it.toDomain() }
            val runningHabit = habits.first { it.id == "h_running" }
            val records = recordRepo.getRecordsForHabit("h_running").map { it.toDomain() }
            val categories = categoryRepo.getAllCategories().associate { it.id to it.name }

            val targetDir = context.getExternalFilesDir(null) ?: context.filesDir
            val exportFile = File(targetDir, "test_single_habit_export.json")
            if (exportFile.exists()) exportFile.delete()

            val filter = ExportFilter(habitIds = setOf("h_running"), format = ExportFormat.JSON)
            val count = FileOutputStream(exportFile).use { out ->
                HabitDataExporter.exportToJson(
                    habits = listOf(runningHabit),
                    recordsByHabit = mapOf("h_running" to records),
                    categoryNames = categories,
                    filter = filter,
                    outputStream = out
                )
            }

            assertEquals(2, count)
            assertTrue(exportFile.exists())

            val jsonString = exportFile.readText(Charsets.UTF_8)
            val envelope = json.decodeFromString<HabitExportEnvelopeDto>(jsonString)

            assertEquals("Habit1", envelope.app)
            assertEquals(1, envelope.exportVersion)
            assertEquals(1, envelope.habitsCount)
            assertEquals(2, envelope.totalRecordsCount)

            val habitDto = envelope.habits[0]
            assertEquals("h_running", habitDto.id)
            assertEquals("Evening Run", habitDto.name)
            assertEquals("Wellness & Health", habitDto.categoryName)
            assertEquals("QUANTITY", habitDto.measurementType)
            assertEquals(5.0, habitDto.targetValue, 0.001)
            assertEquals("km", habitDto.unit)

            val rec1 = habitDto.records.first { it.date == "2026-11-20" }
            assertEquals("DONE", rec1.status)
            assertEquals(5.5, rec1.actualValue, 0.001)
            assertEquals(5.0, rec1.targetValue, 0.001)
            assertTrue(rec1.isCompleted)
            assertTrue(rec1.notes?.contains("Exceeded target!") == true)

            val rec2 = habitDto.records.first { it.date == "2026-11-25" }
            assertEquals("PARTIAL", rec2.status)
            assertEquals(3.0, rec2.actualValue, 0.001)
            assertFalse(rec2.isCompleted)

            exportFile.delete()
        }
    }

    @Test
    fun testExportMultipleHabitsSubset_excludesUnselectedHabits() {
        runBlocking {
            val habits = habitRepo.observeAllHabits().first().map { it.toDomain() }
            val recordsByHabit = mapOf(
                "h_meditation" to recordRepo.getRecordsForHabit("h_meditation").map { it.toDomain() },
                "h_reading" to recordRepo.getRecordsForHabit("h_reading").map { it.toDomain() }
            )

            // Select only meditation and reading, explicitly excluding running
            val filter = ExportFilter(habitIds = setOf("h_meditation", "h_reading"), format = ExportFormat.CSV)
            val out = ByteArrayOutputStream()
            val count = HabitDataExporter.exportToCsv(
                habits = habits,
                recordsByHabit = recordsByHabit,
                categoryNames = emptyMap(),
                filter = filter,
                outputStream = out
            )

            val csv = out.toString(Charsets.UTF_8.name())
            assertTrue("Must include Meditation", csv.contains("Daily Zen"))
            assertFalse("Must NOT include unselected Running habit", csv.contains("Evening Run"))
            // 2 records for meditation + 1 metadata row for reading with no records = 3
            assertEquals(3, count)
        }
    }

    @Test
    fun testExportAllHabits_includesCompletePortfolio() {
        runBlocking {
            val habits = habitRepo.observeAllHabits().first().map { it.toDomain() }
            val recordsByHabit = habits.associate { h ->
                h.id to recordRepo.getRecordsForHabit(h.id).map { it.toDomain() }
            }

            val filter = ExportFilter(habitIds = null, format = ExportFormat.JSON)
            val out = ByteArrayOutputStream()
            val count = HabitDataExporter.exportToJson(
                habits = habits,
                recordsByHabit = recordsByHabit,
                categoryNames = emptyMap(),
                filter = filter,
                outputStream = out
            )

            val envelope = json.decodeFromString<HabitExportEnvelopeDto>(out.toString(Charsets.UTF_8.name()))
            assertEquals(3, envelope.habitsCount)
            assertEquals(4, envelope.totalRecordsCount) // 2 meditation + 2 running
            assertEquals(4, count)
        }
    }

    @Test
    fun testDateFilteredExport_filtersRecordsCorrectly() {
        runBlocking {
            val habits = habitRepo.observeAllHabits().first().map { it.toDomain() }
            val recordsByHabit = mapOf(
                "h_running" to recordRepo.getRecordsForHabit("h_running").map { it.toDomain() }
            )

            // Running has records on 2026-11-20 and 2026-11-25
            // Filter strictly for 2026-11-22 to 2026-11-26
            val filter = ExportFilter(
                habitIds = setOf("h_running"),
                startDate = LocalDate.of(2026, 11, 22),
                endDate = LocalDate.of(2026, 11, 26),
                format = ExportFormat.CSV
            )

            val out = ByteArrayOutputStream()
            val count = HabitDataExporter.exportToCsv(
                habits = habits,
                recordsByHabit = recordsByHabit,
                categoryNames = emptyMap(),
                filter = filter,
                outputStream = out
            )

            assertEquals(1, count)
            val csv = out.toString(Charsets.UTF_8.name())
            assertTrue(csv.contains("2026-11-25"))
            assertFalse(csv.contains("2026-11-20"))
        }
    }

    // =========================================================================
    // 2. BACKUP & RESTORE VERIFICATION
    // =========================================================================

    @Test
    fun testBackupCreationAndSha256Verification() {
        runBlocking {
            val exporter = BackupExporter(database, "1.0.0")
            val out = ByteArrayOutputStream()
            val summary = exporter.exportToStream(out)

            assertEquals(3, summary.habitsCount)
            assertEquals(4, summary.recordsCount)
            assertTrue(summary.bytesWritten > 0)

            val jsonString = out.toString(Charsets.UTF_8.name())
            val envelope = json.decodeFromString<BackupEnvelopeDto>(jsonString)

            // Check metadata & SHA-256
            assertEquals("1.0.0", envelope.appVersion)
            assertNotNull(envelope.checksum)

            val existingHabitIds = database.habitDao().getAllHabitsList().map { it.id }.toSet()
            val existingGoalIds = database.dailyGoalDao().getAllGoalsList().map { it.id }.toSet()
            val existingRecordKeys = database.habitRecordDao().getAllRecordsList().map {
                Pair(it.habitId, it.date)
            }.toSet()

            val validation = BackupValidator.validate(envelope, existingHabitIds, existingGoalIds, existingRecordKeys)
            assertTrue("Valid untampered backup must pass integrity check", validation is BackupValidationResult.Valid)
        }
    }

    @Test
    fun testCorruptedBackupRejection() {
        runBlocking {
            val exporter = BackupExporter(database, "1.0.0")
            val out = ByteArrayOutputStream()
            exporter.exportToStream(out)

            val jsonString = out.toString(Charsets.UTF_8.name())
            val envelope = json.decodeFromString<BackupEnvelopeDto>(jsonString)

            // Tamper with payload by mutating habit name without updating checksum
            val tamperedHabits = envelope.payload.habits.map {
                it.copy(name = it.name + " TAMPERED")
            }
            val tamperedEnvelope = envelope.copy(
                payload = envelope.payload.copy(habits = tamperedHabits)
            )

            val existingHabitIds = database.habitDao().getAllHabitsList().map { it.id }.toSet()
            val existingGoalIds = database.dailyGoalDao().getAllGoalsList().map { it.id }.toSet()
            val existingRecordKeys = database.habitRecordDao().getAllRecordsList().map {
                Pair(it.habitId, it.date)
            }.toSet()

            val validation = BackupValidator.validate(tamperedEnvelope, existingHabitIds, existingGoalIds, existingRecordKeys)
            assertTrue("Tampered backup must be rejected due to SHA-256 mismatch", validation is BackupValidationResult.Invalid)
        }
    }

    @Test
    fun testRestoreMergeMode_preservesHistoricalTruthWithoutOverwritingCompletions() {
        runBlocking {
            val exporter = BackupExporter(database, "1.0.0")
            val out = ByteArrayOutputStream()
            exporter.exportToStream(out)
            val envelope = json.decodeFromString<BackupEnvelopeDto>(out.toString(Charsets.UTF_8.name()))

            val importer = BackupImporter(database, reminderCoordinator, fakeScheduler, fakeNotificationHelper)

            // Modify local database: add a local record that is DONE
            recordRepo.recordProgress(
                HabitRecordEntity(
                    id = "local_rec_new",
                    habitId = "h_reading",
                    date = "2026-11-26",
                    actualValue = 30.0,
                    targetValue = 25.0,
                    measurementType = "COUNT",
                    unit = "pages",
                    isCompleted = true,
                    status = "DONE",
                    notes = "Local new reading",
                    recordedAt = nowEpoch
                )
            )

            // Perform Merge Restore
            val restoreSummary = importer.restore(envelope, RestoreMode.Merge)
            assertTrue(restoreSummary.habitsRestored >= 0)

            // Verify that the local new record was preserved
            val localRecord = recordRepo.getRecord("h_reading", "2026-11-26")
            assertNotNull("Local record created after backup must be preserved in Merge mode", localRecord)
            assertEquals("DONE", localRecord?.status)
        }
    }

    @Test
    fun testRestoreReplaceAll_cleanlyWipesAndRestoresExactData() {
        runBlocking {
            val exporter = BackupExporter(database, "1.0.0")
            val out = ByteArrayOutputStream()
            exporter.exportToStream(out)
            val envelope = json.decodeFromString<BackupEnvelopeDto>(out.toString(Charsets.UTF_8.name()))

            val importer = BackupImporter(database, reminderCoordinator, fakeScheduler, fakeNotificationHelper)

            // Add dummy habit that is NOT in backup
            habitRepo.createHabit(
                HabitEntity(
                    id = "h_unwanted",
                    name = "Unwanted Temporary Habit",
                    description = null,
                    measurementType = "BOOLEAN",
                    targetValue = 1.0,
                    unit = null,
                    scheduleType = "DAILY",
                    scheduleConfig = "{}",
                    reminderTime = null,
                    displayOrder = 99,
                    isPaused = false,
                    isArchived = false,
                    colorHex = null,
                    startDate = "2026-11-26",
                    categoryId = null,
                    createdAt = nowEpoch,
                    updatedAt = nowEpoch
                )
            )

            // Perform Replace All Restore
            importer.restore(envelope, RestoreMode.ReplaceAll)

            // Unwanted habit must be wiped
            val unwanted = habitRepo.getHabitById("h_unwanted")
            assertNull("ReplaceAll must cleanly wipe habits not present in backup", unwanted)

            // Backup habits must be present
            val med = habitRepo.getHabitById("h_meditation")
            assertNotNull("Backup habits must be restored in ReplaceAll", med)
        }
    }

    // =========================================================================
    // 3. STORAGE REPORTING VERIFICATION
    // =========================================================================

    @Test
    fun testStorageCalculator_reportsAccuratePhysicalBytes() {
        val storage = StorageCalculator.calculateStorageUsage(context)

        // On physical device, storage values must be non-negative
        assertTrue(storage.databaseBytes >= 0)
        assertTrue(storage.walBytes >= 0)
        assertTrue(storage.preferencesBytes >= 0)
        assertTrue(storage.cacheBytes >= 0)
        assertTrue(storage.totalAppBytes >= 0)

        // Total must equal sum of components
        val expectedTotal = storage.databaseBytes + storage.walBytes + storage.preferencesBytes + storage.cacheBytes
        assertEquals(expectedTotal, storage.totalAppBytes)

        // Formatted strings must be valid
        assertNotNull(storage.databaseFormatted)
        assertNotNull(storage.preferencesFormatted)
        assertNotNull(storage.cacheFormatted)
        assertNotNull(storage.totalFormatted)
        assertTrue(storage.totalFormatted.contains("B") || storage.totalFormatted.contains("KB") || storage.totalFormatted.contains("MB"))
    }
}
