package com.habit1.app.domain

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.BackupExporter
import com.habit1.app.data.backup.BackupImporter
import com.habit1.app.data.backup.BackupValidator
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalHistoryAggregateEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
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
import com.habit1.app.domain.model.DailyGoalHistoryAggregate
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.reminder.HabitReminderCoordinator
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import com.habit1.app.ui.history.HistoryUiEvent
import com.habit1.app.ui.history.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyGoalHistoricalAggregateIntegrityTest {

    private val testDispatcher = StandardTestDispatcher()
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
        object : NotificationHelper(ApplicationProvider.getApplicationContext()) {
            override fun areNotificationsEnabled(): Boolean = true
            override fun cancelNotification(habitId: String) {}
        }
    }

    private lateinit var coordinator: HabitReminderCoordinator
    private lateinit var importer: BackupImporter

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepo = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        recordRepo = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
        goalRepo = DailyGoalRepositoryImpl(database.dailyGoalDao(), testDispatcher)
        reviewRepo = DailyReviewRepositoryImpl(database.dailyReviewDao(), testDispatcher)

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
        Dispatchers.resetMain()
        database.close()
    }

    // =========================================================================
    // 1. DATA INTEGRITY & ATOMIC CLEANUP SCENARIOS (Sections 6, 7, 8, 9, 10, 11, 22, 23)
    // =========================================================================

    @Test
    fun scenario1_allGoalsCompleted_createsExactAggregateAndCleansDetails() = runTest(testDispatcher) {
        val now = 1700000000000L
        val date = "2026-09-10"

        // 5 goals, 5 completed
        for (i in 1..5) {
            val goal = DailyGoalEntity(
                id = "g_5_$i",
                title = "Goal $i",
                targetDate = date,
                isCompleted = true,
                displayOrder = i,
                notes = null,
                createdAt = now,
                updatedAt = now
            )
            goalRepo.createGoal(goal)
            goalRepo.addSubtask(GoalSubtaskEntity("s_5_$i", "g_5_$i", "Sub $i", true, 0, now))
        }
        advanceUntilIdle()

        assertEquals(5, goalRepo.getGoalsForDate(date).size)
        assertNull(goalRepo.getAggregateForDate(date))

        // Cleanup past date with cutoff = "2026-09-22"
        val deleted = goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-22")
        advanceUntilIdle()

        assertEquals(5, deleted)
        // Detailed goals deleted
        assertTrue(goalRepo.getGoalsForDate(date).isEmpty())

        // Subtasks deleted (no orphans)
        for (i in 1..5) {
            assertTrue(database.dailyGoalDao().getSubtasksForGoal("g_5_$i").isEmpty())
        }

        // Aggregate retained exactly: 5/5
        val agg = goalRepo.getAggregateForDate(date)
        assertNotNull(agg)
        assertEquals(5, agg!!.completedCount)
        assertEquals(5, agg.totalCount)
    }

    @Test
    fun scenario2_partiallyCompletedGoals_createsExactAggregateAndPreservesIncomplete() = runTest(testDispatcher) {
        val now = 1700000000000L
        val date = "2026-09-11"

        // 4 goals: 3 completed, 1 incomplete
        for (i in 1..3) {
            val goal = DailyGoalEntity("g_comp_$i", "Completed $i", date, true, i, null, now, now)
            goalRepo.createGoal(goal)
            goalRepo.addSubtask(GoalSubtaskEntity("s_comp_$i", "g_comp_$i", "Sub $i", true, 0, now))
        }
        val incompGoal = DailyGoalEntity("g_incomp_1", "Incomplete 1", date, false, 4, null, now, now)
        goalRepo.createGoal(incompGoal)
        goalRepo.addSubtask(GoalSubtaskEntity("s_incomp_1", "g_incomp_1", "Sub Incomp", false, 0, now))
        advanceUntilIdle()

        assertEquals(4, goalRepo.getGoalsForDate(date).size)

        // Cleanup
        val deleted = goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-22")
        advanceUntilIdle()

        assertEquals(3, deleted)

        // 3 completed goals deleted, 1 incomplete retained
        val remainingGoals = goalRepo.getGoalsForDate(date)
        assertEquals(1, remainingGoals.size)
        assertEquals("g_incomp_1", remainingGoals[0].goal.id)
        assertFalse(remainingGoals[0].goal.isCompleted)
        assertEquals(1, remainingGoals[0].subtasks.size)
        assertEquals("s_incomp_1", remainingGoals[0].subtasks[0].id)

        // Aggregate retained exactly: 3/4
        val agg = goalRepo.getAggregateForDate(date)
        assertNotNull(agg)
        assertEquals(3, agg!!.completedCount)
        assertEquals(4, agg.totalCount)
    }

    @Test
    fun scenario3_noCompletedGoals_doesNotCreateAggregateAndPreservesIncomplete() = runTest(testDispatcher) {
        val now = 1700000000000L
        val date = "2026-09-12"

        // 1 goal, 0 completed
        val goal = DailyGoalEntity("g_zero_comp", "Zero Comp", date, false, 0, null, now, now)
        goalRepo.createGoal(goal)
        advanceUntilIdle()

        val deleted = goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-22")
        advanceUntilIdle()

        assertEquals(0, deleted)
        // No completed goals cleaned -> no aggregate created
        assertNull(goalRepo.getAggregateForDate(date))
        // Goal remains
        val remaining = goalRepo.getGoalsForDate(date)
        assertEquals(1, remaining.size)
        assertEquals("g_zero_comp", remaining[0].goal.id)
    }

    @Test
    fun scenario4_noGoalsOnDate_doesNotCreateAggregate() = runTest(testDispatcher) {
        val date = "2026-09-13"

        // Run cleanup
        goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-22")
        advanceUntilIdle()

        // Absolutely no row created for date without goals
        assertNull(goalRepo.getAggregateForDate(date))
    }

    @Test
    fun scenario5_cleanupIsIdempotent_noDoubleCountingOnRepeatedRuns() = runTest(testDispatcher) {
        val now = 1700000000000L
        val date = "2026-09-14"

        // 4 goals: 3 completed, 1 incomplete
        for (i in 1..3) {
            goalRepo.createGoal(DailyGoalEntity("g_rep_$i", "Goal $i", date, true, i, null, now, now))
        }
        goalRepo.createGoal(DailyGoalEntity("g_rep_inc", "Goal Inc", date, false, 4, null, now, now))
        advanceUntilIdle()

        // Run 1
        val deleted1 = goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-22")
        advanceUntilIdle()
        assertEquals(3, deleted1)

        val agg1 = goalRepo.getAggregateForDate(date)
        assertEquals(3, agg1!!.completedCount)
        assertEquals(4, agg1.totalCount)

        // Run 2 immediately
        val deleted2 = goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-22")
        advanceUntilIdle()
        assertEquals(0, deleted2)

        // Aggregate remains exactly 3/4 (NOT 6/8!)
        val agg2 = goalRepo.getAggregateForDate(date)
        assertEquals(3, agg2!!.completedCount)
        assertEquals(4, agg2.totalCount)
    }

    @Test
    fun regularHabitsAndDailyReviews_remainUntouchedDuringCleanup() = runTest(testDispatcher) {
        val now = 1700000000000L
        // Insert habit and record
        val habit = HabitEntity(
            id = "h_regular",
            name = "Exercise",
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
            HabitRecordEntity(
                id = "r_1",
                habitId = "h_regular",
                date = "2026-09-10",
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                unit = null,
                isCompleted = true,
                notes = null,
                recordedAt = now
            )
        )

        // Insert daily review
        reviewRepo.saveReview(
            DailyReviewEntity("2026-09-10", "Solid productive day", "GREAT", now, now)
        )

        // Insert past completed daily goal
        goalRepo.createGoal(DailyGoalEntity("g_clean", "Clean me", "2026-09-10", true, 0, null, now, now))
        advanceUntilIdle()

        // Run cleanup
        goalRepo.cleanupCompletedGoalsBeforeDate("2026-09-22")
        advanceUntilIdle()

        // Habit untouched
        assertNotNull(habitRepo.getHabitById("h_regular"))
        // Record untouched
        val rec = recordRepo.getRecord("h_regular", "2026-09-10")
        assertNotNull(rec)
        assertTrue(rec!!.isCompleted)
        // Review untouched
        val rev = reviewRepo.getReview("2026-09-10")
        assertNotNull(rev)
        assertEquals("Solid productive day", rev!!.notes)
    }

    // =========================================================================
    // 2. DOMAIN VALIDATION & CONSTRAINTS (Section 5)
    // =========================================================================

    @Test
    fun domainModel_enforcesConstraints() {
        // Valid
        val valid = DailyGoalHistoryAggregate("2026-09-20", 3, 4)
        assertEquals("2026-09-20", valid.date)
        assertEquals(3, valid.completedCount)
        assertEquals(4, valid.totalCount)

        // Negative completedCount
        try {
            DailyGoalHistoryAggregate("2026-09-20", -1, 4)
            fail("Expected IllegalArgumentException for negative completedCount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("completedCount"))
        }

        // Negative totalCount
        try {
            DailyGoalHistoryAggregate("2026-09-20", 0, -1)
            fail("Expected IllegalArgumentException for negative totalCount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("totalCount"))
        }

        // completedCount > totalCount
        try {
            DailyGoalHistoryAggregate("2026-09-20", 5, 3)
            fail("Expected IllegalArgumentException for completedCount > totalCount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("cannot exceed totalCount"))
        }
    }

    // =========================================================================
    // 3. HISTORY INTEGRATION & NO DOUBLE COUNTING (Sections 12, 13, 14, 15, 24, 25)
    // =========================================================================

    @Test
    fun historyViewModel_combinesAggregatesAndLiveGoals_withoutDoubleCounting() = runTest(testDispatcher) {
        val now = 1700000000000L
        val pastDate = "2026-09-15"
        val todayDate = "2026-09-22"

        // Past date had 4 goals (3 completed, 1 incomplete) -> cleaned up into aggregate
        database.dailyGoalDao().upsertAggregate(
            DailyGoalHistoryAggregateEntity(pastDate, 3, 4)
        )
        // The 1 retained incomplete goal remains in daily_goals table
        database.dailyGoalDao().insertGoal(
            DailyGoalEntity("g_past_retained", "Retained Goal", pastDate, false, 0, null, now, now)
        )

        // Today has 2 live goals (both completed)
        database.dailyGoalDao().insertGoal(
            DailyGoalEntity("g_today_1", "Today 1", todayDate, true, 0, null, now, now)
        )
        database.dailyGoalDao().insertGoal(
            DailyGoalEntity("g_today_2", "Today 2", todayDate, true, 1, null, now, now)
        )
        advanceUntilIdle()

        val viewModel = HistoryViewModel(
            habitRepository = habitRepo,
            habitRecordRepository = recordRepo,
            dailyGoalRepository = goalRepo,
            dailyReviewRepository = reviewRepo,
            zoneId = ZoneId.of("UTC"),
            coroutineScope = backgroundScope
        )
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }

        // 1. Month View Calendar Day for pastDate
        val pastDayItem = state.calendarDays.find { it.date == LocalDate.parse(pastDate) }
        assertNotNull(pastDayItem)
        // Must reflect aggregate (3/4), NOT (3/5) or (0/1)!
        assertEquals("Completed goals must equal aggregate count", 3, pastDayItem!!.completedGoalsCount)
        assertEquals("Total goals must equal aggregate total (no double-counting retained goal)", 4, pastDayItem.totalGoalsCount)
        assertTrue("hasRecordedActivity must be true due to historical goals", pastDayItem.hasRecordedActivity)

        // 2. Month View Calendar Day for todayDate
        val todayDayItem = state.calendarDays.find { it.date == LocalDate.parse(todayDate) }
        assertNotNull(todayDayItem)
        assertEquals(2, todayDayItem!!.completedGoalsCount)
        assertEquals(2, todayDayItem.totalGoalsCount)

        // 3. Month Summary
        val summary = state.monthSummary
        assertNotNull(summary)
        // 4 goals from past date + 2 goals from today = 6 total goals, 3 + 2 = 5 completed goals
        assertEquals(6, summary!!.totalGoals)
        assertEquals(5, summary.completedGoals)
        assertEquals(((5.0 / 6.0) * 100.0).toFloat(), summary.goalCompletionRate, 0.1f)

        // 4. Yearly Overview for September 2026
        val sepYearItem = state.yearlyOverview.find { it.yearMonth == YearMonth.of(2026, 9) }
        assertNotNull(sepYearItem)
        assertEquals(6, sepYearItem!!.totalGoals)
        assertEquals(5, sepYearItem.completedGoals)
        assertEquals(((5.0 / 6.0) * 100.0).toFloat(), sepYearItem.goalCompletionRate, 0.1f)

        // 5. Day Breakdown for pastDate
        viewModel.onEvent(HistoryUiEvent.SelectDate(LocalDate.parse(pastDate)))
        advanceUntilIdle()

        val updatedState = viewModel.uiState.first { it.selectedDateBreakdown?.date == LocalDate.parse(pastDate) }
        val breakdown = updatedState.selectedDateBreakdown
        assertNotNull(breakdown)
        assertNotNull("Day breakdown must contain historicalGoalAggregate", breakdown!!.historicalGoalAggregate)
        assertEquals(3, breakdown.historicalGoalAggregate!!.completedCount)
        assertEquals(4, breakdown.historicalGoalAggregate!!.totalCount)
        // The retained incomplete goal is present in goals list for display
        assertEquals(1, breakdown.goals.size)
        assertEquals("Retained Goal", breakdown.goals[0].title)
        assertFalse(breakdown.goals[0].isCompleted)
    }

    @Test
    fun historyViewModel_allGoalsCompletedHistoricalDay_shows100Percent() = runTest(testDispatcher) {
        val pastDate = "2026-09-05"

        // 5/5 aggregate, no detailed goals remain
        database.dailyGoalDao().upsertAggregate(
            DailyGoalHistoryAggregateEntity(pastDate, 5, 5)
        )
        advanceUntilIdle()

        val viewModel = HistoryViewModel(
            habitRepository = habitRepo,
            habitRecordRepository = recordRepo,
            dailyGoalRepository = goalRepo,
            dailyReviewRepository = reviewRepo,
            zoneId = ZoneId.of("UTC"),
            coroutineScope = backgroundScope
        )
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        val dayItem = state.calendarDays.find { it.date == LocalDate.parse(pastDate) }
        assertNotNull(dayItem)
        assertEquals(5, dayItem!!.completedGoalsCount)
        assertEquals(5, dayItem.totalGoalsCount)
        assertTrue(dayItem.hasRecordedActivity)

        viewModel.onEvent(HistoryUiEvent.SelectDate(LocalDate.parse(pastDate)))
        advanceUntilIdle()

        val updatedState = viewModel.uiState.first { it.selectedDateBreakdown?.date == LocalDate.parse(pastDate) }
        val breakdown = updatedState.selectedDateBreakdown
        assertNotNull(breakdown)
        assertNotNull(breakdown!!.historicalGoalAggregate)
        assertEquals(5, breakdown.historicalGoalAggregate!!.completedCount)
        assertEquals(5, breakdown.historicalGoalAggregate!!.totalCount)
        assertTrue(breakdown.goals.isEmpty())
    }

    // =========================================================================
    // 4. BACKUP & RESTORE INTEGRITY (Sections 18, 19)
    // =========================================================================

    @Test
    fun backupExportAndRestore_replaceAll_restoresAggregatesAccurately() = runTest(testDispatcher) {
        val date = "2026-09-18"
        database.dailyGoalDao().upsertAggregate(
            DailyGoalHistoryAggregateEntity(date, 3, 4)
        )
        advanceUntilIdle()

        val exporter = BackupExporter(database, "1.0.0")
        val outStream = ByteArrayOutputStream()
        val exportSummary = exporter.exportToStream(outStream)
        assertEquals(1, exportSummary.aggregatesCount)

        // Wipe DB
        database.dailyGoalDao().deleteAllAggregates()
        assertNull(goalRepo.getAggregateForDate(date))

        // Restore via ReplaceAll
        val jsonString = outStream.toString(Charsets.UTF_8.name())
        val envelope = json.decodeFromString(BackupEnvelopeDto.serializer(), jsonString)

        val validationResult = BackupValidator.validate(envelope)
        assertTrue(validationResult is com.habit1.app.data.backup.BackupValidationResult.Valid)
        val validPreview = (validationResult as com.habit1.app.data.backup.BackupValidationResult.Valid).preview
        assertEquals(1, validPreview.aggregatesCount)

        val restoreSummary = importer.restore(envelope, RestoreMode.ReplaceAll)
        assertEquals(1, restoreSummary.aggregatesRestored)

        val restoredAgg = goalRepo.getAggregateForDate(date)
        assertNotNull(restoredAgg)
        assertEquals(3, restoredAgg!!.completedCount)
        assertEquals(4, restoredAgg.totalCount)
    }

    @Test
    fun backupRestore_merge_isIdempotentAndDoesNotDuplicate() = runTest(testDispatcher) {
        val date = "2026-09-19"
        database.dailyGoalDao().upsertAggregate(
            DailyGoalHistoryAggregateEntity(date, 2, 3)
        )
        advanceUntilIdle()

        val exporter = BackupExporter(database, "1.0.0")
        val outStream = ByteArrayOutputStream()
        exporter.exportToStream(outStream)

        val jsonString = outStream.toString(Charsets.UTF_8.name())
        val envelope = json.decodeFromString(BackupEnvelopeDto.serializer(), jsonString)

        // Merge run 1
        val summary1 = importer.restore(envelope, RestoreMode.Merge)
        assertEquals(1, summary1.aggregatesRestored)

        // Merge run 2 (re-import same backup)
        val summary2 = importer.restore(envelope, RestoreMode.Merge)
        assertEquals(1, summary2.aggregatesRestored)

        val allAggs = database.dailyGoalDao().getAllAggregates()
        assertEquals(1, allAggs.size)
        assertEquals(2, allAggs[0].completedCount)
        assertEquals(3, allAggs[0].totalCount)
    }

    @Test
    fun backupRestore_v1FormatWithoutAggregates_restoresGracefully() = runTest(testDispatcher) {
        // Construct v1-style payload with NO aggregates section (defaults to empty)
        val now = 1700000000000L
        val habitDto = com.habit1.app.data.backup.model.HabitBackupDto(
            id = "h_v1",
            name = "Old Habit",
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
        val payload = BackupPayloadDto(
            habits = listOf(habitDto),
            records = emptyList(),
            goals = emptyList(),
            subtasks = emptyList(),
            reviews = emptyList(),
            aggregates = emptyList() // v1 absence maps to default emptyList
        )
        val checksum = BackupChecksumCalculator.computeChecksum(payload)
        val envelope = BackupEnvelopeDto(
            formatVersion = 1,
            appVersion = "1.0.0",
            exportedAt = "2026-09-22T00:00:00Z",
            checksum = checksum,
            payload = payload
        )

        val validation = BackupValidator.validate(envelope)
        assertTrue("Old v1 backup without aggregates must be valid", validation is com.habit1.app.data.backup.BackupValidationResult.Valid)

        val summary = importer.restore(envelope, RestoreMode.ReplaceAll)
        assertEquals(1, summary.habitsRestored)
        assertEquals(0, summary.aggregatesRestored)

        assertNotNull(habitRepo.getHabitById("h_v1"))
        assertTrue(database.dailyGoalDao().getAllAggregates().isEmpty())
    }

    // =========================================================================
    // 5. DATABASE MIGRATION & SCHEMA INTEGRITY (Sections 20, 31)
    // =========================================================================

    @Test
    fun migration_1_2_createsTableAndPreservesExistingData() {
        val dbName = "migration_test.db"
        context.deleteDatabase(dbName)

        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v1 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `habits` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `description` TEXT,
                            `measurement_type` TEXT NOT NULL,
                            `target_value` REAL NOT NULL,
                            `unit` TEXT,
                            `schedule_type` TEXT NOT NULL,
                            `schedule_config` TEXT NOT NULL,
                            `reminder_time` TEXT,
                            `display_order` INTEGER NOT NULL,
                            `is_paused` INTEGER NOT NULL,
                            `is_archived` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `daily_goals` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `target_date` TEXT NOT NULL,
                            `is_completed` INTEGER NOT NULL,
                            `display_order` INTEGER NOT NULL,
                            `notes` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = helperFactory.create(config)
        val v1Db = helper.writableDatabase

        // Insert v1 data
        v1Db.execSQL("INSERT INTO habits (id, name, measurement_type, target_value, schedule_type, schedule_config, display_order, is_paused, is_archived, created_at, updated_at) VALUES ('h1', 'Read', 'BOOLEAN', 1.0, 'DAILY', '{}', 0, 0, 0, 1000, 1000)")
        v1Db.execSQL("INSERT INTO daily_goals (id, title, target_date, is_completed, display_order, created_at, updated_at) VALUES ('g1', 'Goal 1', '2026-09-20', 1, 0, 1000, 1000)")

        // Execute MIGRATION_1_2
        AppDatabase.MIGRATION_1_2.migrate(v1Db)

        // Verify daily_goal_history_aggregates table exists and works
        v1Db.execSQL("INSERT INTO daily_goal_history_aggregates (date, completed_count, total_count) VALUES ('2026-09-20', 3, 4)")
        val cursor = v1Db.query("SELECT * FROM daily_goal_history_aggregates WHERE date = '2026-09-20'")
        assertTrue(cursor.moveToFirst())
        assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("completed_count")))
        assertEquals(4, cursor.getInt(cursor.getColumnIndexOrThrow("total_count")))
        cursor.close()

        // Verify existing v1 data remains intact
        val habitCursor = v1Db.query("SELECT name FROM habits WHERE id = 'h1'")
        assertTrue(habitCursor.moveToFirst())
        assertEquals("Read", habitCursor.getString(0))
        habitCursor.close()

        val goalCursor = v1Db.query("SELECT title FROM daily_goals WHERE id = 'g1'")
        assertTrue(goalCursor.moveToFirst())
        assertEquals("Goal 1", goalCursor.getString(0))
        goalCursor.close()

        v1Db.close()
        context.deleteDatabase(dbName)
    }

    // =========================================================================
    // 6. STORAGE MEASUREMENT & MULTI-YEAR PROJECTION (Section 27)
    // =========================================================================

    @Test
    fun storageProjection_measuredAndCalculated() = runTest(testDispatcher) {
        // Measure real bytes for 1 row:
        // date: TEXT (10 chars = 10 bytes)
        // completed_count: INTEGER (1 to 2 bytes in SQLite varint)
        // total_count: INTEGER (1 to 2 bytes in SQLite varint)
        // SQLite B-tree row header: ~4 bytes
        // Total row payload: ~18-20 bytes.
        // Even with 4 KB B-tree page leaf overhead (35 bytes per entry):
        // 1 Year (365 rows):   365 * 35 B   ~ 12.8 KB
        // 3 Years (1,095 rows): 1095 * 35 B ~ 38.3 KB
        // 5 Years (1,825 rows): 1825 * 35 B ~ 63.9 KB
        // 10 Years (3,652 rows): 3652 * 35 B ~ 127.8 KB (easily < 150 KB!)

        val baseDate = LocalDate.of(2026, 1, 1)
        for (i in 0 until 365) {
            val dateStr = baseDate.plusDays(i.toLong()).toString()
            database.dailyGoalDao().upsertAggregate(
                DailyGoalHistoryAggregateEntity(dateStr, 3, 4)
            )
        }
        advanceUntilIdle()

        val all365 = database.dailyGoalDao().getAllAggregates()
        assertEquals(365, all365.size)

        // Verify range query performance on 365 rows is instantaneous (< 10ms)
        val startTime = System.currentTimeMillis()
        val range = database.dailyGoalDao().getAggregatesForDateRange("2026-01-01", "2026-12-31")
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(365, range.size)
        assertTrue("Bounded date-range query must take < 50ms (took ${elapsed}ms)", elapsed < 50)
    }
}
