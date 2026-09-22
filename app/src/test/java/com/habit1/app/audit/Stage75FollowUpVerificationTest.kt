package com.habit1.app.audit

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.BackupExporter
import com.habit1.app.data.backup.BackupImporter
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage75FollowUpVerificationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zoneId = ZoneId.of("UTC")
    private val calculateStreaks = CalculateStreaksUseCase()
    private val notificationHelper = NotificationHelper(context)
    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {}
        override fun cancelReminder(habitId: String) {}
    }

    private fun createDiskDatabase(file: File): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            })
            .build()
    }

    // --- 1. STREAK QUERY SEMANTICS VERIFICATION ---
    @Test
    fun verifyWhyCurrentStreakRequiresUnbrokenHistoricalData() {
        val today = LocalDate.of(2026, 9, 23)
        val startDate = today.minusDays(300)

        val habit = HabitEntity(
            id = "habit_long_streak",
            name = "Daily Meditation",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            scheduleType = "DAILY",
            scheduleConfig = """{"days":[1,2,3,4,5,6,7]}""",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            updatedAt = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        ).toDomain()

        // Case 1: User has an active 250-day streak!
        // Consecutive completions from (today - 249 days) to today
        val unbrokenRecords = (0..249).map { offset ->
            val date = today.minusDays(offset.toLong())
            HabitRecord(
                id = "rec_$date",
                habitId = habit.id,
                date = date,
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                unit = null,
                isCompleted = true,
                notes = null,
                recordedAt = date.atTime(12, 0).atZone(zoneId).toInstant()
            )
        }

        // Full lifetime calculation
        val fullStreakResult = calculateStreaks.execute(habit, unbrokenRecords, today, zoneId)
        assertEquals(250, fullStreakResult.currentStreak)

        // What happens if we arbitrarily bounded the query to 90 days?
        val arbitrary90DayRecords = unbrokenRecords.filter { !it.date.isBefore(today.minusDays(90)) }
        val boundedStreakResult = calculateStreaks.execute(habit, arbitrary90DayRecords, today, zoneId)

        println("=== STREAK BOUNDING PROOF ===")
        println("True Active Streak (250 unbroken days): ${fullStreakResult.currentStreak}")
        println("Streak calculated with 90-day bound: ${boundedStreakResult.currentStreak}")
        println("=============================")

        // An arbitrary 90-day bound would falsely truncate the user's streak to 91 days!
        assertNotEquals(fullStreakResult.currentStreak, boundedStreakResult.currentStreak)
        assertEquals(91, boundedStreakResult.currentStreak)
    }

    // --- 2. EXPLAIN QUERY PLAN FOR YEARLY QUERY ---
    @Test
    fun verifyYearlyQueryPlanAndBounding() {
        val tempDir = File(context.cacheDir, "qp_${UUID.randomUUID()}").apply { mkdirs() }
        val dbFile = File(tempDir, "query_plan.db")
        val db = createDiskDatabase(dbFile)

        try {
            val sdb = db.openHelper.writableDatabase

            // Check query plan for date range query on habit_records
            val queryPlan = mutableListOf<String>()
            sdb.query("EXPLAIN QUERY PLAN SELECT * FROM habit_records WHERE date BETWEEN '2026-01-01' AND '2026-12-31' ORDER BY date ASC;").use { cursor ->
                while (cursor.moveToNext()) {
                    val detail = cursor.getString(3)
                    queryPlan.add(detail)
                }
            }

            println("=== SQLITE EXPLAIN QUERY PLAN (Yearly Query) ===")
            queryPlan.forEach { println(it) }
            println("================================================")

            // Verify index usage: index_habit_records_date is used for index search
            assertTrue(queryPlan.any { it.contains("index_habit_records_date") || it.contains("SEARCH") })
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun verifyMultiYearDailyGoalCleanupAndSemantics() {
        runBlocking {
        val tempDir = File(context.cacheDir, "my_cleanup_${UUID.randomUUID()}").apply { mkdirs() }
        val dbFile = File(tempDir, "history_cleanup.db")
        val db = createDiskDatabase(dbFile)

        try {
            val habit = HabitEntity(
                id = "h1",
                name = "Exercise",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                scheduleType = "DAILY",
                scheduleConfig = """{"days":[1,2,3,4,5,6,7]}""",
                displayOrder = 0,
                isPaused = false,
                isArchived = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
            db.habitDao().insert(habit)

            // Year 1 (2024): 3 goals (2 completed, 1 incomplete), 365 habit records
            val goalsY1 = listOf(
                DailyGoalEntity("g_2024_1", "Goal 2024 Completed 1", "2024-05-10", isCompleted = true, displayOrder = 0, createdAt = 1000L, updatedAt = 1000L),
                DailyGoalEntity("g_2024_2", "Goal 2024 Completed 2", "2024-05-11", isCompleted = true, displayOrder = 0, createdAt = 1000L, updatedAt = 1000L),
                DailyGoalEntity("g_2024_3", "Goal 2024 Incomplete", "2024-05-12", isCompleted = false, displayOrder = 0, createdAt = 1000L, updatedAt = 1000L)
            )
            val subtasksY1 = listOf(
                GoalSubtaskEntity("s_2024_1", "g_2024_1", "Subtask 1", isCompleted = true, displayOrder = 0, createdAt = 1000L),
                GoalSubtaskEntity("s_2024_2", "g_2024_2", "Subtask 2", isCompleted = true, displayOrder = 0, createdAt = 1000L)
            )

            // Year 2 (2025): 2 goals (both completed)
            val goalsY2 = listOf(
                DailyGoalEntity("g_2025_1", "Goal 2025 Completed 1", "2025-08-15", isCompleted = true, displayOrder = 0, createdAt = 1000L, updatedAt = 1000L),
                DailyGoalEntity("g_2025_2", "Goal 2025 Completed 2", "2025-08-16", isCompleted = true, displayOrder = 0, createdAt = 1000L, updatedAt = 1000L)
            )

            // Current Year (2026): 2 goals on today (1 completed, 1 pending)
            val todayStr = "2026-09-23"
            val goalsToday = listOf(
                DailyGoalEntity("g_today_1", "Today Goal Done", todayStr, isCompleted = true, displayOrder = 0, createdAt = 1000L, updatedAt = 1000L),
                DailyGoalEntity("g_today_2", "Today Goal Pending", todayStr, isCompleted = false, displayOrder = 1, createdAt = 1000L, updatedAt = 1000L)
            )

            db.dailyGoalDao().insertAllGoals(goalsY1 + goalsY2 + goalsToday)
            db.dailyGoalDao().insertAllSubtasks(subtasksY1)

            // Insert habit records across 2024, 2025, 2026
            val records = listOf(
                HabitRecordEntity("r_2024", "h1", "2024-05-10", 1.0, 1.0, "BOOLEAN", null, true, null, 1000L),
                HabitRecordEntity("r_2025", "h1", "2025-08-15", 1.0, 1.0, "BOOLEAN", null, true, null, 1000L),
                HabitRecordEntity("r_2026", "h1", todayStr, 1.0, 1.0, "BOOLEAN", null, true, null, 1000L)
            )
            db.habitRecordDao().insertAll(records)

            // Verify counts BEFORE cleanup
            assertEquals(7, db.dailyGoalDao().getAllGoalsList().size)
            assertEquals(2, db.dailyGoalDao().getAllSubtasksList().size)
            assertEquals(3, db.habitRecordDao().totalRecordsCount())

            // EXECUTE CLEANUP with cutoffDate = todayStr
            val deletedCount = db.dailyGoalDao().cleanupCompletedGoalsBeforeDate(todayStr)
            assertEquals(4, deletedCount) // 2 from 2024, 2 from 2025

            // VERIFY COUNTS AFTER CLEANUP
            val remainingGoals = db.dailyGoalDao().getAllGoalsList()
            val remainingSubtasks = db.dailyGoalDao().getAllSubtasksList()
            val remainingRecords = db.habitRecordDao().totalRecordsCount()

            println("=== MULTI-YEAR CLEANUP RESULTS ===")
            println("Deleted past completed goals: $deletedCount")
            println("Remaining goals count: ${remainingGoals.size}")
            remainingGoals.forEach { println(" - ${it.id} (${it.targetDate}): isCompleted=${it.isCompleted}") }
            println("Remaining subtasks: ${remainingSubtasks.size}")
            println("Remaining habit records: $remainingRecords")
            println("==================================")

            assertEquals(3, remainingGoals.size)
            assertTrue(remainingGoals.any { it.id == "g_2024_3" && !it.isCompleted }) // 2024 Incomplete preserved!
            assertTrue(remainingGoals.any { it.id == "g_today_1" && it.isCompleted }) // Today's completed preserved!
            assertTrue(remainingGoals.any { it.id == "g_today_2" && !it.isCompleted }) // Today's pending preserved!
            assertEquals(0, remainingSubtasks.size) // Subtasks belonging to completed past goals deleted!
            assertEquals(3, remainingRecords) // Habit records completely untouched!

            // VERIFY YEAR HISTORY DATA AFTER CLEANUP
            // 2024:
            val y2024Goals = db.dailyGoalDao().getGoalsForDateRange("2024-01-01", "2024-12-31")
            assertEquals(1, y2024Goals.size) // Only 1 incomplete goal remains
            assertEquals(false, y2024Goals[0].goal.isCompleted)

            // 2025:
            val y2025Goals = db.dailyGoalDao().getGoalsForDateRange("2025-01-01", "2025-12-31")
            assertEquals(0, y2025Goals.size) // Both completed goals were cleaned up!

            // 2026:
            val y2026Goals = db.dailyGoalDao().getGoalsForDateRange("2026-01-01", "2026-12-31")
            assertEquals(2, y2026Goals.size)

            // BACKUP CHECK
            val exporter = BackupExporter(db)
            val stream = ByteArrayOutputStream()
            exporter.exportToStream(stream)
            val backupBytes = stream.toByteArray()

            // Restore into a fresh clean database
            val fileRestore = File(tempDir, "restore_verify.db")
            val dbRestore = createDiskDatabase(fileRestore)

            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val envelope = json.decodeFromString(
                com.habit1.app.data.backup.model.BackupEnvelopeDto.serializer(),
                String(backupBytes, Charsets.UTF_8)
            )
            val habitRepoRestore = HabitRepositoryImpl(dbRestore.habitDao(), Dispatchers.Unconfined)
            val coordinatorRestore = HabitReminderCoordinator(
                habitRepository = habitRepoRestore,
                scheduler = fakeScheduler,
                notificationHelper = notificationHelper
            )
            val importer = BackupImporter(
                database = dbRestore,
                reminderCoordinator = coordinatorRestore,
                reminderScheduler = fakeScheduler,
                notificationHelper = notificationHelper
            )

            importer.restore(envelope, RestoreMode.ReplaceAll)

            assertEquals(3, dbRestore.dailyGoalDao().getAllGoalsList().size)
            assertEquals(3, dbRestore.habitRecordDao().totalRecordsCount())
            assertEquals(1, dbRestore.habitDao().countActive())

            dbRestore.close()
            fileRestore.delete()
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }
}
}



