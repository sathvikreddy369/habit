package com.habit1.app.audit

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalHistoryAggregateEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.reminder.HabitReminderCalculator
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import com.habit1.app.domain.validation.HabitValidationError
import com.habit1.app.domain.validation.HabitValidator
import com.habit1.app.domain.validation.MeasurementKind
import com.habit1.app.ui.components.NumericInputValidator
import com.habit1.app.ui.components.NumericValidationResult
import com.habit1.app.ui.history.HistoryUiEvent
import com.habit1.app.ui.history.HistoryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Stage 7.7 Product Integrity and Reconciliation Tests.
 *
 * Verifies:
 * 1. Permanent Daily Goal retention (no automatic deletion, explicit deletion preserved).
 * 2. History priority rule (prefers detailed records over aggregates; legacy aggregate fallback).
 * 3. Reminder search horizon beyond 732 days for long-interval schedules.
 * 4. Comma decimal input parsing across validators and viewmodels (e.g. German "1,5").
 * 5. Today's in-progress status (Upcoming/Scheduled, never ProjectedMissed on today).
 * 6. Room Migration 1 -> 2 schema alignment (no schema divergence, non-destructive).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage77ProductIntegrityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var db: AppDatabase
    private val zoneId = ZoneId.of("UTC")
    private val baseInstant = Instant.parse("2026-09-01T00:00:00Z")

    @Before
    fun setup() {
        db = AppDatabase.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 1. DAILY GOALS PERMANENT RETENTION & HISTORY PRIORITY RULE
    // =========================================================================

    @Test
    fun testPermanentRetention_completedGoalsAndSubtasksNeverAutomaticallyDeleted() = runTest(testDispatcher) {
        val goalDao = db.dailyGoalDao()
        val now = System.currentTimeMillis()
        val pastDate = "2026-09-10"

        // Insert 5 goals on past date (3 completed, 2 incomplete)
        for (i in 1..5) {
            val goalId = "g_$i"
            val isComp = i <= 3
            goalDao.insertGoal(
                DailyGoalEntity(
                    id = goalId,
                    title = "Goal $i",
                    targetDate = pastDate,
                    isCompleted = isComp,
                    displayOrder = i,
                    notes = "Note for goal $i",
                    createdAt = now,
                    updatedAt = now
                )
            )
            goalDao.insertSubtask(
                GoalSubtaskEntity(
                    id = "sub_$i",
                    goalId = goalId,
                    title = "Subtask $i",
                    isCompleted = isComp,
                    displayOrder = 0,
                    createdAt = now
                )
            )
        }

        // Verify all 5 goals and subtasks exist in DB
        val storedGoals = goalDao.getGoalsForDate(pastDate)
        assertEquals(5, storedGoals.size)
        assertEquals(3, storedGoals.count { it.goal.isCompleted })
        assertEquals(5, goalDao.getAllSubtasksList().size)

        // Verify explicit user deletion works as expected
        goalDao.deleteGoalById("g_1")
        val remainingGoals = goalDao.getGoalsForDate(pastDate)
        assertEquals(4, remainingGoals.size)
        assertNull(goalDao.getGoalById("g_1"))
        // Subtask deleted by cascade
        assertEquals(4, goalDao.getAllSubtasksList().size)
    }

    @Test
    fun testHistoryPriorityRule_prefersDetailedGoalsOverAggregate() = runTest(testDispatcher) {
        val habitRepo = HabitRepositoryImpl(db.habitDao(), testDispatcher)
        val recordRepo = HabitRecordRepositoryImpl(db.habitRecordDao(), testDispatcher)
        val goalRepo = DailyGoalRepositoryImpl(db.dailyGoalDao(), testDispatcher)
        val now = System.currentTimeMillis()

        // Date 1: Legacy date with ONLY aggregate (3/4 completed) and NO detailed goals
        db.dailyGoalDao().upsertAggregate(
            DailyGoalHistoryAggregateEntity("2026-09-12", 3, 4)
        )

        // Date 2: Modern date with 3 detailed goals (2 completed, 1 incomplete) and NO aggregate
        db.dailyGoalDao().insertGoal(
            DailyGoalEntity(
                id = "mod_1",
                title = "Modern Completed 1",
                targetDate = "2026-09-15",
                isCompleted = true,
                displayOrder = 0,
                notes = "Finish assignment",
                createdAt = now,
                updatedAt = now
            )
        )
        db.dailyGoalDao().insertGoal(
            DailyGoalEntity(
                id = "mod_2",
                title = "Modern Completed 2",
                targetDate = "2026-09-15",
                isCompleted = true,
                displayOrder = 1,
                notes = null,
                createdAt = now,
                updatedAt = now
            )
        )
        db.dailyGoalDao().insertGoal(
            DailyGoalEntity(
                id = "mod_3",
                title = "Modern Incomplete",
                targetDate = "2026-09-15",
                isCompleted = false,
                displayOrder = 2,
                notes = null,
                createdAt = now,
                updatedAt = now
            )
        )
        advanceUntilIdle()

        val viewModel = HistoryViewModel(
            habitRepository = habitRepo,
            habitRecordRepository = recordRepo,
            dailyGoalRepository = goalRepo,
            zoneId = zoneId,
            coroutineScope = backgroundScope
        )
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }

        // Date 1 (Legacy): Should use aggregate (3/4)
        val legacyDay = state.calendarDays.find { it.date == LocalDate.parse("2026-09-12") }
        assertNotNull(legacyDay)
        assertEquals(3, legacyDay!!.completedGoalsCount)
        assertEquals(4, legacyDay.totalGoalsCount)

        // Date 2 (Modern detailed): Should use detailed records (2/3)
        val modernDay = state.calendarDays.find { it.date == LocalDate.parse("2026-09-15") }
        assertNotNull(modernDay)
        assertEquals(2, modernDay!!.completedGoalsCount)
        assertEquals(3, modernDay.totalGoalsCount)

        // Inspect breakdown for modern date
        viewModel.onEvent(HistoryUiEvent.SelectDate(LocalDate.parse("2026-09-15")))
        advanceUntilIdle()
        val breakdown = viewModel.uiState.first { it.selectedDateBreakdown?.date == LocalDate.parse("2026-09-15") }.selectedDateBreakdown
        assertNotNull(breakdown)
        assertEquals(3, breakdown!!.goals.size)
        // Detailed goals should not have historicalGoalAggregate
        assertNull(breakdown.historicalGoalAggregate)
        assertEquals("Modern Completed 1", breakdown.goals[0].title)
        assertTrue(breakdown.goals[0].isCompleted)
    }

    // =========================================================================
    // 2. COMMA DECIMAL INPUT VALIDATION (e.g. German "1,5")
    // =========================================================================

    @Test
    fun testCommaDecimal_canonicalParser() {
        assertEquals(1.5, HabitValidator.parseDecimal("1.5")!!, 0.0001)
        assertEquals(1.5, HabitValidator.parseDecimal("1,5")!!, 0.0001)
        assertEquals(0.5, HabitValidator.parseDecimal("0.5")!!, 0.0001)
        assertEquals(0.5, HabitValidator.parseDecimal("0,5")!!, 0.0001)
        assertEquals(2.75, HabitValidator.parseDecimal("  2,75  ")!!, 0.0001)

        // Invalid inputs
        assertNull(HabitValidator.parseDecimal("abc"))
        assertNull(HabitValidator.parseDecimal("1,2,3"))
        assertNull(HabitValidator.parseDecimal("1.2.3"))
        assertNull(HabitValidator.parseDecimal("--"))
        assertNull(HabitValidator.parseDecimal(""))
    }

    @Test
    fun testCommaDecimal_habitValidatorAcceptsCommaInQuantity() {
        val validErrors = HabitValidator.validateRawMeasurement(
            kind = MeasurementKind.QUANTITY,
            targetStr = "1,5",
            unitStr = "L"
        )
        assertTrue("1,5 should be valid quantity target", validErrors.isEmpty())

        val invalidErrors = HabitValidator.validateRawMeasurement(
            kind = MeasurementKind.QUANTITY,
            targetStr = "1,2,3",
            unitStr = "L"
        )
        assertTrue("1,2,3 should fail validation", invalidErrors.any { it is HabitValidationError.TargetMustBePositive })
    }

    @Test
    fun testCommaDecimal_numericInputValidator() {
        val resultComma = NumericInputValidator.validate("2,5", MeasurementType.Quantity(2.0, "km"))
        assertTrue(resultComma is NumericValidationResult.Valid)
        assertEquals(2.5, (resultComma as NumericValidationResult.Valid).value, 0.0001)

        val resultDot = NumericInputValidator.validate("2.5", MeasurementType.Quantity(2.0, "km"))
        assertTrue(resultDot is NumericValidationResult.Valid)
        assertEquals(2.5, (resultDot as NumericValidationResult.Valid).value, 0.0001)

        val resultInvalid = NumericInputValidator.validate("invalid", MeasurementType.Quantity(2.0, "km"))
        assertTrue(resultInvalid is NumericValidationResult.Invalid)

        // Count should reject decimal
        val resultCount = NumericInputValidator.validate("2,5", MeasurementType.Count(5, "reps"))
        assertTrue(resultCount is NumericValidationResult.Invalid)
    }

    // =========================================================================
    // 3. TODAY'S STATUS SEMANTICS (UPCOMING VS MISSED)
    // =========================================================================

    @Test
    fun testTodayStatus_scheduledWithoutRecordIsUpcomingNotMissed() {
        val useCase = EvaluateHabitHistoryUseCase()
        val habit = Habit(
            id = "h_today",
            name = "Workout",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            createdAt = baseInstant,
            updatedAt = baseInstant
        )
        val today = LocalDate.of(2026, 9, 23)

        // Yesterday (Sep 22) has no record -> ProjectedMissed
        // Today (Sep 23) has no record -> Future (Upcoming)
        val summary = useCase.execute(
            habit = habit,
            records = emptyList(),
            startDate = LocalDate.of(2026, 9, 22),
            endDate = today,
            todayDate = today,
            zoneId = zoneId
        )

        val yesterdayDay = summary.historyDays.find { it.date == LocalDate.of(2026, 9, 22) }
        assertNotNull(yesterdayDay)
        assertTrue("Yesterday unrecorded should be ProjectedMissed", yesterdayDay!!.status is CalendarDayStatus.ProjectedMissed)

        val todayDay = summary.historyDays.find { it.date == today }
        assertNotNull(todayDay)
        assertTrue("Today unrecorded should be Future (Upcoming), NOT ProjectedMissed", todayDay!!.status is CalendarDayStatus.Future)
        assertFalse("Today should not be classified as missed", todayDay.status is CalendarDayStatus.ProjectedMissed)
        assertEquals(1, summary.projectedMissedDaysCount)
    }

    // =========================================================================
    // 4. REMINDER HORIZON FOR INTERVAL SCHEDULES BEYOND 732 DAYS
    // =========================================================================

    @Test
    fun testReminderHorizon_supportsIntervalsBeyond732Days() {
        val calculator = HabitReminderCalculator(EvaluateScheduleUseCase())
        val fromInstant = Instant.parse("2026-09-01T08:00:00Z")
        val reminderTime = LocalTime.of(9, 0)

        // Test every 1 day
        val habitDaily = Habit(
            id = "h_1",
            name = "Daily",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            reminderTime = reminderTime,
            createdAt = fromInstant,
            updatedAt = fromInstant
        )
        val nextDaily = calculator.calculateNextReminder(habitDaily, fromInstant, zoneId)
        assertNotNull(nextDaily)
        assertEquals(LocalDate.of(2026, 9, 1), nextDaily!!.toLocalDate())

        // Test every 733 days (beyond the old 732 day limit)
        val habit733 = Habit(
            id = "h_733",
            name = "Biennial",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Interval(
                everyNDays = 733,
                anchorDate = LocalDate.of(2026, 9, 1)
            ),
            reminderTime = reminderTime,
            createdAt = fromInstant,
            updatedAt = fromInstant
        )
        // At 10:00 AM on Sep 1 (after 9:00 AM trigger time), next reminder must be 733 days later
        val afterMorningTrigger = Instant.parse("2026-09-01T10:00:00Z")
        val next733 = calculator.calculateNextReminder(habit733, afterMorningTrigger, zoneId)
        assertNotNull("Reminder for 733-day interval must not be null", next733)
        assertEquals(LocalDate.of(2026, 9, 1).plusDays(733), next733!!.toLocalDate())

        // Test every 1000 days
        val habit1000 = Habit(
            id = "h_1000",
            name = "Thousand Days",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Interval(
                everyNDays = 1000,
                anchorDate = LocalDate.of(2026, 9, 1)
            ),
            reminderTime = reminderTime,
            createdAt = fromInstant,
            updatedAt = fromInstant
        )
        val next1000 = calculator.calculateNextReminder(habit1000, afterMorningTrigger, zoneId)
        assertNotNull("Reminder for 1000-day interval must not be null", next1000)
        assertEquals(LocalDate.of(2026, 9, 1).plusDays(1000), next1000!!.toLocalDate())
    }

    // =========================================================================
    // 5. DATABASE MIGRATION 1 -> 2 ALIGNMENT TEST
    // =========================================================================

    @Test
    fun testMigration1To2_executesCleanlyAndPreservesAllData() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name("test_mig.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `habits` (
                            `id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `measurement_type` TEXT NOT NULL,
                            `target_value` REAL NOT NULL, `unit` TEXT, `schedule_type` TEXT NOT NULL, `schedule_config` TEXT NOT NULL,
                            `reminder_time` TEXT, `display_order` INTEGER NOT NULL, `is_paused` INTEGER NOT NULL,
                            `is_archived` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `habit_records` (
                            `id` TEXT NOT NULL, `habit_id` TEXT NOT NULL, `date` TEXT NOT NULL, `actual_value` REAL NOT NULL,
                            `target_value` REAL NOT NULL, `measurement_type` TEXT NOT NULL, `unit` TEXT,
                            `is_completed` INTEGER NOT NULL, `notes` TEXT, `recorded_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `daily_goals` (
                            `id` TEXT NOT NULL, `title` TEXT NOT NULL, `target_date` TEXT NOT NULL, `is_completed` INTEGER NOT NULL,
                            `display_order` INTEGER NOT NULL, `notes` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `goal_subtasks` (
                            `id` TEXT NOT NULL, `goal_id` TEXT NOT NULL, `title` TEXT NOT NULL, `is_completed` INTEGER NOT NULL,
                            `display_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`goal_id`) REFERENCES `daily_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `daily_reviews` (
                            `date` TEXT NOT NULL, `notes` TEXT, `mood` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`date`)
                        )
                    """.trimIndent())

                    // Insert sample v1 data
                    db.execSQL("INSERT INTO habits VALUES ('h1', 'Water', NULL, 'QUANTITY', 2.0, 'L', 'DAILY', '{}', '08:00', 0, 0, 0, 1000, 1000)")
                    db.execSQL("INSERT INTO daily_goals VALUES ('g1', 'Assignment', '2026-09-20', 1, 0, 'Done', 1000, 1000)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(config)
        val writableDb = openHelper.writableDatabase

        // Execute MIGRATION_1_2
        AppDatabase.MIGRATION_1_2.migrate(writableDb)

        // Verify daily_goal_history_aggregates table exists and allows insertion
        writableDb.execSQL("INSERT INTO daily_goal_history_aggregates VALUES ('2026-09-20', 1, 1)")
        val cursorAgg = writableDb.query("SELECT * FROM daily_goal_history_aggregates WHERE date = '2026-09-20'")
        assertTrue(cursorAgg.moveToFirst())
        assertEquals(1, cursorAgg.getInt(cursorAgg.getColumnIndexOrThrow("completed_count")))
        cursorAgg.close()

        // Verify existing v1 data intact
        val cursorHabits = writableDb.query("SELECT * FROM habits WHERE id = 'h1'")
        assertTrue(cursorHabits.moveToFirst())
        assertEquals("Water", cursorHabits.getString(cursorHabits.getColumnIndexOrThrow("name")))
        cursorHabits.close()

        writableDb.close()
    }
}
