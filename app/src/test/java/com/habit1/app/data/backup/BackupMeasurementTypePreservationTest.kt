package com.habit1.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.mapper.EntityMappers.toEntity
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
import java.io.ByteArrayOutputStream
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupMeasurementTypePreservationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: java.time.ZoneId) {}
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
    fun testAllFourMeasurementTypes_preservedDistinctlyThroughBackupAndRestore() = runTest(testDispatcher) {
        val now = Instant.ofEpochMilli(1700000000000L)

        // 1. Create domain habits representing all four distinct measurement types
        val booleanHabit = Habit(
            id = "habit_bool",
            name = "Floss Teeth",
            description = "Dental hygiene daily",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            reminderTime = null,
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )

        val countHabit = Habit(
            id = "habit_count",
            name = "Pushups",
            description = "Daily pushup progression",
            measurement = MeasurementType.Count(target = 30, unit = "reps"),
            schedule = HabitSchedule.Daily,
            reminderTime = null,
            displayOrder = 1,
            isPaused = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )

        val durationHabit = Habit(
            id = "habit_duration",
            name = "Deep Work Session",
            description = "Focused coding without distractions",
            measurement = MeasurementType.Duration(targetMinutes = 90),
            schedule = HabitSchedule.Daily,
            reminderTime = null,
            displayOrder = 2,
            isPaused = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )

        val quantityHabit = Habit(
            id = "habit_quantity",
            name = "Water Intake",
            description = "Stay hydrated throughout the workday",
            measurement = MeasurementType.Quantity(target = 3.25, unit = "Liters"),
            schedule = HabitSchedule.Daily,
            reminderTime = null,
            displayOrder = 3,
            isPaused = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )

        // Insert into source database
        sourceDb.habitDao().insert(booleanHabit.toEntity())
        sourceDb.habitDao().insert(countHabit.toEntity())
        sourceDb.habitDao().insert(durationHabit.toEntity())
        sourceDb.habitDao().insert(quantityHabit.toEntity())

        // 2. Create records with snapshots of each measurement type
        val records = listOf(
            HabitRecordEntity(
                id = "rec_bool",
                habitId = "habit_bool",
                date = "2026-09-21",
                isCompleted = true,
                actualValue = 1.0,
                targetValue = 1.0,
                unit = null,
                measurementType = MeasurementType.BooleanChoice.TYPE_NAME, // "BOOLEAN"
                notes = "Flossed after dinner",
                recordedAt = 1000L
            ),
            HabitRecordEntity(
                id = "rec_count",
                habitId = "habit_count",
                date = "2026-09-21",
                isCompleted = true,
                actualValue = 30.0,
                targetValue = 30.0,
                unit = "reps",
                measurementType = MeasurementType.Count.TYPE_NAME, // "COUNT"
                notes = "3 sets of 10",
                recordedAt = 1001L
            ),
            HabitRecordEntity(
                id = "rec_duration",
                habitId = "habit_duration",
                date = "2026-09-21",
                isCompleted = false,
                actualValue = 60.0,
                targetValue = 90.0,
                unit = "min",
                measurementType = MeasurementType.Duration.TYPE_NAME, // "DURATION"
                notes = "Interrupted after 1 hour",
                recordedAt = 1002L
            ),
            HabitRecordEntity(
                id = "rec_quantity",
                habitId = "habit_quantity",
                date = "2026-09-21",
                isCompleted = true,
                actualValue = 3.5,
                targetValue = 3.25,
                unit = "Liters",
                measurementType = MeasurementType.Quantity.TYPE_NAME, // "QUANTITY"
                notes = "Exceeded target",
                recordedAt = 1003L
            )
        )
        sourceDb.habitRecordDao().insertAll(records)

        // 3. Export to JSON
        val exporter = BackupExporter(sourceDb, "1.0.0")
        val baos = ByteArrayOutputStream()
        exporter.exportToStream(baos)

        val jsonString = String(baos.toByteArray(), Charsets.UTF_8)
        val envelope = json.decodeFromString<BackupEnvelopeDto>(jsonString)

        // Verify JSON payload preserves distinct measurement types
        val payloadHabits = envelope.payload.habits.associateBy { it.id }
        assertEquals(MeasurementType.BooleanChoice.TYPE_NAME, payloadHabits["habit_bool"]?.measurementType)
        assertEquals(MeasurementType.Count.TYPE_NAME, payloadHabits["habit_count"]?.measurementType)
        assertEquals(MeasurementType.Duration.TYPE_NAME, payloadHabits["habit_duration"]?.measurementType)
        assertEquals(MeasurementType.Quantity.TYPE_NAME, payloadHabits["habit_quantity"]?.measurementType)

        // 4. Validate through BackupValidator
        val validationResult = BackupValidator.validate(envelope)
        assertTrue("Validation must pass for all 4 measurement types", validationResult is BackupValidationResult.Valid)

        // 5. Restore into clean target database
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

        val summary = importer.restore(envelope, RestoreMode.ReplaceAll)
        assertEquals(4, summary.habitsRestored)
        assertEquals(4, summary.recordsRestored)

        // 6. Map restored entities back to domain and assert measurement semantics
        val restoredEntities = targetDb.habitDao().getAllHabitsList().associateBy { it.id }

        val restoredBool = restoredEntities["habit_bool"]?.toDomain()
        assertNotNull(restoredBool)
        assertTrue(
            "Boolean habit must be restored as MeasurementType.BooleanChoice, was ${restoredBool?.measurement}",
            restoredBool?.measurement is MeasurementType.BooleanChoice
        )
        assertEquals("Dental hygiene daily", restoredBool?.description)

        val restoredCount = restoredEntities["habit_count"]?.toDomain()
        assertNotNull(restoredCount)
        assertTrue(
            "Count habit must be restored as MeasurementType.Count, was ${restoredCount?.measurement}",
            restoredCount?.measurement is MeasurementType.Count
        )
        val countMeasurement = restoredCount?.measurement as MeasurementType.Count
        assertEquals(30, countMeasurement.target)
        assertEquals("reps", countMeasurement.unit)
        assertEquals("Daily pushup progression", restoredCount.description)

        val restoredDuration = restoredEntities["habit_duration"]?.toDomain()
        assertNotNull(restoredDuration)
        assertTrue(
            "Duration habit must be restored as MeasurementType.Duration, was ${restoredDuration?.measurement}",
            restoredDuration?.measurement is MeasurementType.Duration
        )
        val durationMeasurement = restoredDuration?.measurement as MeasurementType.Duration
        assertEquals(90, durationMeasurement.targetMinutes)
        assertEquals("Focused coding without distractions", restoredDuration.description)

        val restoredQuantity = restoredEntities["habit_quantity"]?.toDomain()
        assertNotNull(restoredQuantity)
        assertTrue(
            "Quantity habit must be restored as MeasurementType.Quantity, was ${restoredQuantity?.measurement}",
            restoredQuantity?.measurement is MeasurementType.Quantity
        )
        val quantityMeasurement = restoredQuantity?.measurement as MeasurementType.Quantity
        assertEquals(3.25, quantityMeasurement.target, 0.001)
        assertEquals("Liters", quantityMeasurement.unit)
        assertEquals("Stay hydrated throughout the workday", restoredQuantity.description)

        // 7. Verify historical records preserve snapshot measurement types
        val restoredRecords = targetDb.habitRecordDao().getAllRecordsList().associateBy { it.id }
        assertEquals(MeasurementType.BooleanChoice.TYPE_NAME, restoredRecords["rec_bool"]?.measurementType)
        assertEquals(MeasurementType.Count.TYPE_NAME, restoredRecords["rec_count"]?.measurementType)
        assertEquals(MeasurementType.Duration.TYPE_NAME, restoredRecords["rec_duration"]?.measurementType)
        assertEquals(MeasurementType.Quantity.TYPE_NAME, restoredRecords["rec_quantity"]?.measurementType)

        assertEquals("reps", restoredRecords["rec_count"]?.unit)
        assertEquals("min", restoredRecords["rec_duration"]?.unit)
        assertEquals("Liters", restoredRecords["rec_quantity"]?.unit)
        assertEquals(null, restoredRecords["rec_bool"]?.unit)
    }
}
