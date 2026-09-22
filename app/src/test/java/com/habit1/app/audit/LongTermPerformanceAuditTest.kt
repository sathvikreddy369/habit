package com.habit1.app.audit

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.BackupExporter
import com.habit1.app.data.backup.BackupImporter
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.toBackupDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LongTermPerformanceAuditTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zoneId = ZoneId.of("UTC")
    private val computeAnalytics = ComputeHabitAnalyticsUseCase()
    private val calculateStreaks = CalculateStreaksUseCase()

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: com.habit1.app.domain.model.Habit, fromInstant: Instant, zoneId: ZoneId) {}
        override fun cancelReminder(habitId: String) {}
    }

    private val notificationHelper = NotificationHelper(context)

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

    data class FixtureResult(
        val years: Int,
        val days: Int,
        val habitCount: Int,
        val recordCount: Int,
        val goalCount: Int,
        val subtaskCount: Int,
        val reviewCount: Int,
        val dbFileSize: Long,
        val pageSize: Long,
        val pageCount: Long,
        val backupJsonSize: Long,
        val backupExportTimeMs: Long,
        val checksumTimeMs: Long,
        val yearHistoryQueryTimeMs: Long,
        val yearAggregationTimeMs: Long,
        val habit1YAnalyticsTimeMs: Long,
        val todayStreakCalcTimeMs: Long
    )

    private fun populateFixture(
        db: AppDatabase,
        years: Int,
        habitCount: Int,
        dailyGoalFrequencyPerWeek: Int,
        reviewFrequencyPerWeek: Int,
        endDate: LocalDate = LocalDate.of(2026, 12, 31)
    ): FixtureResult = runBlocking {
        val totalDays = years * 365
        val startDate = endDate.minusDays(totalDays.toLong() - 1)

        // 1. Create habits
        val habits = (1..habitCount).map { i ->
            val measurementType = when (i % 4) {
                0 -> "BOOLEAN"
                1 -> "COUNT"
                2 -> "DURATION"
                else -> "QUANTITY"
            }
            val target = when (measurementType) {
                "BOOLEAN" -> 1.0
                "COUNT" -> 20.0
                "DURATION" -> 30.0
                else -> 2.5
            }
            val unit = when (measurementType) {
                "BOOLEAN" -> null
                "COUNT" -> "reps"
                "DURATION" -> "mins"
                else -> "liters"
            }
            HabitEntity(
                id = "habit_$i",
                name = "Habit $i",
                description = "Long term test habit $i",
                measurementType = measurementType,
                targetValue = target,
                unit = unit,
                scheduleType = "DAILY",
                scheduleConfig = """{"days":[1,2,3,4,5,6,7]}""",
                reminderTime = if (i % 2 == 0) "08:30" else null,
                displayOrder = i,
                isPaused = false,
                isArchived = false,
                createdAt = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                updatedAt = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            )
        }
        db.habitDao().insertAll(habits)

        // 2. Generate records, goals, reviews in batches for performance
        val records = ArrayList<HabitRecordEntity>(totalDays * habitCount)
        val goals = ArrayList<DailyGoalEntity>()
        val subtasks = ArrayList<GoalSubtaskEntity>()
        val reviews = ArrayList<DailyReviewEntity>()

        var currentDate = startDate
        var dayIndex = 0
        while (!currentDate.isAfter(endDate)) {
            val dateStr = currentDate.toString()

            // Records: ~80% completion consistency
            for (hIndex in 1..habitCount) {
                val isCompleted = (dayIndex + hIndex) % 5 != 0 // 80% completion
                val actual = if (isCompleted) habits[hIndex - 1].targetValue else (habits[hIndex - 1].targetValue * 0.4)
                records.add(
                    HabitRecordEntity(
                        id = "rec_${hIndex}_$dateStr",
                        habitId = "habit_$hIndex",
                        date = dateStr,
                        actualValue = actual,
                        targetValue = habits[hIndex - 1].targetValue,
                        measurementType = habits[hIndex - 1].measurementType,
                        unit = habits[hIndex - 1].unit,
                        isCompleted = isCompleted,
                        notes = if (dayIndex % 30 == 0) "Monthly milestone" else null,
                        recordedAt = currentDate.atTime(20, 0).atZone(zoneId).toInstant().toEpochMilli()
                    )
                )
            }

            // Goals: according to frequency
            if (dayIndex % 7 < dailyGoalFrequencyPerWeek) {
                val goalId = "goal_$dateStr"
                val goalCompleted = dayIndex % 4 != 0 // 75% completed
                goals.add(
                    DailyGoalEntity(
                        id = goalId,
                        title = "Key Goal for $dateStr",
                        targetDate = dateStr,
                        isCompleted = goalCompleted,
                        displayOrder = 0,
                        notes = "Priority task",
                        createdAt = currentDate.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli(),
                        updatedAt = currentDate.atTime(18, 0).atZone(zoneId).toInstant().toEpochMilli()
                    )
                )
                // Subtasks: 2 per goal
                subtasks.add(
                    GoalSubtaskEntity(
                        id = "sub_1_$goalId",
                        goalId = goalId,
                        title = "Subtask 1",
                        isCompleted = goalCompleted,
                        displayOrder = 0,
                        createdAt = currentDate.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()
                    )
                )
                subtasks.add(
                    GoalSubtaskEntity(
                        id = "sub_2_$goalId",
                        goalId = goalId,
                        title = "Subtask 2",
                        isCompleted = goalCompleted,
                        displayOrder = 1,
                        createdAt = currentDate.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()
                    )
                )
            }

            // Reviews
            if (dayIndex % 7 < reviewFrequencyPerWeek) {
                reviews.add(
                    DailyReviewEntity(
                        date = dateStr,
                        notes = "Reflected on productivity and consistency.",
                        mood = if (dayIndex % 2 == 0) "GREAT" else "GOOD",
                        createdAt = currentDate.atTime(21, 0).atZone(zoneId).toInstant().toEpochMilli(),
                        updatedAt = currentDate.atTime(21, 0).atZone(zoneId).toInstant().toEpochMilli()
                    )
                )
            }

            // Batch insert to avoid OOM or SQLite variable limit
            if (records.size >= 5000) {
                db.habitRecordDao().insertAll(records)
                records.clear()
            }
            if (goals.size >= 1000) {
                db.dailyGoalDao().insertAllGoals(goals)
                goals.clear()
            }
            if (subtasks.size >= 2000) {
                db.dailyGoalDao().insertAllSubtasks(subtasks)
                subtasks.clear()
            }
            if (reviews.size >= 1000) {
                db.dailyReviewDao().upsertAll(reviews)
                reviews.clear()
            }

            currentDate = currentDate.plusDays(1)
            dayIndex++
        }

        // Insert remaining
        if (records.isNotEmpty()) db.habitRecordDao().insertAll(records)
        if (goals.isNotEmpty()) db.dailyGoalDao().insertAllGoals(goals)
        if (subtasks.isNotEmpty()) db.dailyGoalDao().insertAllSubtasks(subtasks)
        if (reviews.isNotEmpty()) db.dailyReviewDao().upsertAll(reviews)

        // Force checkpoint to flush WAL into main DB file
        val sdb = db.openHelper.writableDatabase
        sdb.query("PRAGMA wal_checkpoint(FULL);").use { it.moveToFirst() }

        var pageSize = 4096L
        sdb.query("PRAGMA page_size;").use { cursor ->
            if (cursor.moveToFirst()) pageSize = cursor.getLong(0)
        }
        var pageCount = 0L
        sdb.query("PRAGMA page_count;").use { cursor ->
            if (cursor.moveToFirst()) pageCount = cursor.getLong(0)
        }

        val totalRecords = db.habitRecordDao().totalRecordsCount()
        val totalGoals = db.dailyGoalDao().getAllGoalsList().size
        val totalSubtasks = db.dailyGoalDao().getAllSubtasksList().size
        val totalReviews = db.dailyReviewDao().getAllReviews().size

        // Measure Year History Query & In-Memory Aggregation for 2026
        val yearStart = "2026-01-01"
        val yearEnd = "2026-12-31"
        var yearRecords: List<HabitRecordEntity>
        var yearGoals: List<com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks>
        val yearQueryTime = measureTimeMillis {
            yearRecords = db.habitRecordDao().getRecordsForDateRange(yearStart, yearEnd)
            yearGoals = db.dailyGoalDao().getGoalsForDateRange(yearStart, yearEnd)
        }

        val yearAggTime = measureTimeMillis {
            val recByDate = yearRecords.groupBy { it.date }
            val goalsByDate = yearGoals.groupBy { it.goal.targetDate }
            val months = (1..12).map { m ->
                val monthStart = LocalDate.of(2026, m, 1)
                val monthEnd = monthStart.plusMonths(1).minusDays(1)
                var completedHabits = 0
                var scheduledHabits = 0
                var d = monthStart
                while (!d.isAfter(monthEnd)) {
                    val ds = d.toString()
                    val dayRecs = recByDate[ds] ?: emptyList()
                    completedHabits += dayRecs.count { it.isCompleted }
                    scheduledHabits += habitCount
                    d = d.plusDays(1)
                }
                val monthGoals = goalsByDate.filterKeys {
                    val ld = LocalDate.parse(it)
                    ld.monthValue == m
                }.values.flatten()
                val completedGoals = monthGoals.count { it.goal.isCompleted }
                Triple(m, completedHabits, completedGoals)
            }
            assertEquals(12, months.size)
        }

        // Measure 1Y analytics calculation for habit_1
        val habit1Entity = db.habitDao().getById("habit_1")!!
        val habit1Domain = habit1Entity.toDomain()
        val habit1Range = AnalyticsRange.ofYearsEndingAt(endDate, 1)
        val habit1Records = db.habitRecordDao().getRecordsForHabitInRange(
            "habit_1",
            habit1Range.startDate.toString(),
            habit1Range.endDate.toString()
        ).map { it.toDomain() }

        val analyticsTime = measureTimeMillis {
            computeAnalytics.execute(
                habit = habit1Domain,
                records = habit1Records,
                range = habit1Range,
                todayDate = endDate,
                zoneId = zoneId
            )
        }

        // Measure Today streak calculation (fetching all history as currently written in TodayViewModel)
        val habitIds = habits.map { it.id }
        val streakTime = measureTimeMillis {
            val allHistory = db.habitRecordDao().getRecordsForHabits(habitIds).groupBy { it.habitId }
            habits.forEach { h ->
                val hDomain = h.toDomain()
                val hHistory = (allHistory[h.id] ?: emptyList()).map { it.toDomain() }
                calculateStreaks.execute(hDomain, hHistory, endDate, zoneId)
            }
        }

        // Measure Backup export & Checksum
        val exporter = BackupExporter(db)
        val outStream = ByteArrayOutputStream()
        val exportTime = measureTimeMillis {
            exporter.exportToStream(outStream)
        }
        val backupJsonBytes = outStream.toByteArray()

        // Direct checksum calculation time
        val allHabitsList = db.habitDao().getAllHabitsList().map { it.toBackupDto() }
        val allRecordsList = db.habitRecordDao().getAllRecordsList().map { it.toBackupDto() }
        val allGoalsList = db.dailyGoalDao().getAllGoalsList().map { it.toBackupDto() }
        val allSubtasksList = db.dailyGoalDao().getAllSubtasksList().map { it.toBackupDto() }
        val allReviewsList = db.dailyReviewDao().getAllReviews().map { it.toBackupDto() }
        val payloadDto = com.habit1.app.data.backup.model.BackupPayloadDto(
            habits = allHabitsList,
            records = allRecordsList,
            goals = allGoalsList,
            subtasks = allSubtasksList,
            reviews = allReviewsList
        )
        val checksumTime = measureTimeMillis {
            BackupChecksumCalculator.computeChecksum(payloadDto)
        }

        FixtureResult(
            years = years,
            days = totalDays,
            habitCount = habitCount,
            recordCount = totalRecords,
            goalCount = totalGoals,
            subtaskCount = totalSubtasks,
            reviewCount = totalReviews,
            dbFileSize = pageSize * pageCount,
            pageSize = pageSize,
            pageCount = pageCount,
            backupJsonSize = backupJsonBytes.size.toLong(),
            backupExportTimeMs = exportTime,
            checksumTimeMs = checksumTime,
            yearHistoryQueryTimeMs = yearQueryTime,
            yearAggregationTimeMs = yearAggTime,
            habit1YAnalyticsTimeMs = analyticsTime,
            todayStreakCalcTimeMs = streakTime
        )
    }

    @Test
    fun benchmarkMultiYearGrowthAndPerformance() {
        val tempDir = File(context.cacheDir, "audit_benchmarks_${UUID.randomUUID()}").apply { mkdirs() }

        try {
            // Profile B (Typical User: 10 habits, 4 goals/week, 3 reviews/week)
            val scenarios = listOf(1, 3, 5, 10)
            val results = mutableListOf<FixtureResult>()

            for (years in scenarios) {
                val dbFile = File(tempDir, "typical_${years}yr.db")
                val db = createDiskDatabase(dbFile)
                try {
                    val result = populateFixture(
                        db = db,
                        years = years,
                        habitCount = 10,
                        dailyGoalFrequencyPerWeek = 4,
                        reviewFrequencyPerWeek = 3
                    )
                    results.add(result)
                    println("=== TYPICAL PROFILE: $years YEAR(S) ($years x 365 = ${result.days} days) ===")
                    println("Records: ${result.recordCount}, Goals: ${result.goalCount}, Subtasks: ${result.subtaskCount}, Reviews: ${result.reviewCount}")
                    println("DB File Size: ${result.dbFileSize / 1024} KB (${result.pageCount} pages x ${result.pageSize} bytes)")
                    println("Backup JSON Size: ${result.backupJsonSize / 1024} KB")
                    println("Backup Export Time: ${result.backupExportTimeMs} ms, Checksum Time: ${result.checksumTimeMs} ms")
                    println("Year History Query: ${result.yearHistoryQueryTimeMs} ms, In-Memory Aggregation: ${result.yearAggregationTimeMs} ms")
                    println("Habit 1Y Analytics: ${result.habit1YAnalyticsTimeMs} ms")
                    println("Today All-Habits Streak Calculation: ${result.todayStreakCalcTimeMs} ms")
                    println("========================================================================\n")
                } finally {
                    db.close()
                    dbFile.delete()
                }
            }

            // Verify assertions on results
            assertEquals(4, results.size)
            // Verify Year History query is fast across all years (< 200 ms)
            assertTrue(results.all { it.yearHistoryQueryTimeMs < 200 })
            // Verify 1Y analytics query is fast (< 100 ms)
            assertTrue(results.all { it.habit1YAnalyticsTimeMs < 100 })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun benchmarkProfilesAtThreeYears() {
        val tempDir = File(context.cacheDir, "profiles_3yr_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            // Profile A: Light (5 habits, 1 goal/week, 1 review/week)
            val fileA = File(tempDir, "light_3yr.db")
            val dbA = createDiskDatabase(fileA)
            val resA = populateFixture(dbA, years = 3, habitCount = 5, dailyGoalFrequencyPerWeek = 1, reviewFrequencyPerWeek = 1)
            dbA.close()

            // Profile B: Typical (10 habits, 4 goals/week, 3 reviews/week)
            val fileB = File(tempDir, "typical_3yr.db")
            val dbB = createDiskDatabase(fileB)
            val resB = populateFixture(dbB, years = 3, habitCount = 10, dailyGoalFrequencyPerWeek = 4, reviewFrequencyPerWeek = 3)
            dbB.close()

            // Profile C: Heavy (20 habits, 7 goals/week, 7 reviews/week)
            val fileC = File(tempDir, "heavy_3yr.db")
            val dbC = createDiskDatabase(fileC)
            val resC = populateFixture(dbC, years = 3, habitCount = 20, dailyGoalFrequencyPerWeek = 7, reviewFrequencyPerWeek = 7)
            dbC.close()

            println("=== 3-YEAR PROFILES COMPARISON ===")
            println("Light (5 habits): DB=${resA.dbFileSize / 1024} KB, Backup=${resA.backupJsonSize / 1024} KB, Records=${resA.recordCount}")
            println("Typical (10 habits): DB=${resB.dbFileSize / 1024} KB, Backup=${resB.backupJsonSize / 1024} KB, Records=${resB.recordCount}")
            println("Heavy (20 habits): DB=${resC.dbFileSize / 1024} KB, Backup=${resC.backupJsonSize / 1024} KB, Records=${resC.recordCount}")
            println("==================================\n")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun benchmarkDailyGoalCleanupScalability() = runBlocking {
        val tempDir = File(context.cacheDir, "cleanup_benchmarks_${UUID.randomUUID()}").apply { mkdirs() }
        val dbFile = File(tempDir, "cleanup.db")
        val db = createDiskDatabase(dbFile)

        try {
            val counts = listOf(100, 1000, 10000)

            for (count in counts) {
                // Insert 'count' completed goals in the past (2025)
                val goals = (1..count).map { i ->
                    DailyGoalEntity(
                        id = "goal_clean_${count}_$i",
                        title = "Goal $i to clean",
                        targetDate = "2025-06-01",
                        isCompleted = true,
                        displayOrder = i,
                        createdAt = 1000L,
                        updatedAt = 2000L
                    )
                }
                // Each has 2 subtasks
                val subtasks = goals.flatMap { g ->
                    listOf(
                        GoalSubtaskEntity(id = "sub1_${g.id}", goalId = g.id, title = "Sub 1", isCompleted = true, displayOrder = 0, createdAt = 1000L),
                        GoalSubtaskEntity(id = "sub2_${g.id}", goalId = g.id, title = "Sub 2", isCompleted = true, displayOrder = 1, createdAt = 1000L)
                    )
                }
                // Also insert 50 incomplete past goals (must NOT be deleted)
                val incompleteGoals = (1..50).map { i ->
                    DailyGoalEntity(
                        id = "goal_preserve_${count}_$i",
                        title = "Incomplete Goal $i",
                        targetDate = "2025-06-01",
                        isCompleted = false,
                        displayOrder = i,
                        createdAt = 1000L,
                        updatedAt = 2000L
                    )
                }

                db.dailyGoalDao().insertAllGoals(goals)
                db.dailyGoalDao().insertAllSubtasks(subtasks)
                db.dailyGoalDao().insertAllGoals(incompleteGoals)

                val cutoffDate = "2026-01-01"
                val deletedGoals: Int
                val cleanupTimeMs = measureTimeMillis {
                    deletedGoals = db.dailyGoalDao().cleanupCompletedGoalsBeforeDate(cutoffDate)
                }

                println("=== DAILY GOAL CLEANUP: $count ELIGIBLE GOALS ===")
                println("Deleted: $deletedGoals goals, Time: $cleanupTimeMs ms")

                // Verify subtasks were also deleted
                val remainingSubtasks = db.dailyGoalDao().getAllSubtasksList()
                val remainingGoals = db.dailyGoalDao().getAllGoalsList()

                println("Remaining subtasks in DB: ${remainingSubtasks.size}")
                println("Remaining goals in DB: ${remainingGoals.size} (expected 50 incomplete goals)")
                println("=================================================\n")

                assertEquals(count, deletedGoals)
                assertEquals(50, remainingGoals.size)
                assertEquals(0, remainingSubtasks.size) // Only deleted goals had subtasks

                // Clean up remaining for next loop
                db.dailyGoalDao().deleteAllGoals()
            }
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun benchmarkRestoreModesAndDuplicateCheck() = runBlocking {
        val tempDir = File(context.cacheDir, "restore_benchmarks_${UUID.randomUUID()}").apply { mkdirs() }
        val dbFile = File(tempDir, "restore.db")
        val db = createDiskDatabase(dbFile)

        try {
            // Populate 1 year of typical data
            populateFixture(db, years = 1, habitCount = 10, dailyGoalFrequencyPerWeek = 4, reviewFrequencyPerWeek = 3)

            val exporter = BackupExporter(db)
            val stream = ByteArrayOutputStream()
            exporter.exportToStream(stream)
            val jsonBytes = stream.toByteArray()

            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val envelope = json.decodeFromString(
                com.habit1.app.data.backup.model.BackupEnvelopeDto.serializer(),
                String(jsonBytes, Charsets.UTF_8)
            )

            val habitRepo = HabitRepositoryImpl(db.habitDao(), Dispatchers.Unconfined)
            val coordinator = HabitReminderCoordinator(
                habitRepository = habitRepo,
                scheduler = fakeScheduler,
                notificationHelper = notificationHelper
            )
            val importer = BackupImporter(
                database = db,
                reminderCoordinator = coordinator,
                reminderScheduler = fakeScheduler,
                notificationHelper = notificationHelper
            )

            // Measure Replace Restore
            val replaceTime = measureTimeMillis {
                val summary = importer.restore(envelope, RestoreMode.ReplaceAll)
                assertEquals(10, summary.habitsRestored)
            }
            println("=== RESTORE PERFORMANCE (1-Year Payload) ===")
            println("REPLACE_ALL Time: $replaceTime ms")

            // Measure Merge Restore (Idempotent replay - no duplicate explosion)
            val countBeforeMerge = db.habitRecordDao().totalRecordsCount()
            val mergeTime = measureTimeMillis {
                importer.restore(envelope, RestoreMode.Merge)
            }
            val countAfterMerge = db.habitRecordDao().totalRecordsCount()

            println("MERGE Time: $mergeTime ms")
            println("Record count before merge: $countBeforeMerge, after merge: $countAfterMerge")
            println("============================================\n")

            assertEquals(countBeforeMerge, countAfterMerge) // No duplicates!
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }
}
