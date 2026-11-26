package com.habit1.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration4To5Test {

    @Test
    fun testMigration4To5_executesCleanlyAndPreservesHistoricalTruth() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name("test_migration_4_5.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v4 schema
                    db.execSQL(
                        """
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
                            `color_hex` TEXT DEFAULT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_is_archived_display_order` ON `habits` (`is_archived`, `display_order`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_created_at` ON `habits` (`created_at`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `habit_records` (
                            `id` TEXT NOT NULL,
                            `habit_id` TEXT NOT NULL,
                            `date` TEXT NOT NULL,
                            `actual_value` REAL NOT NULL,
                            `target_value` REAL NOT NULL,
                            `measurement_type` TEXT NOT NULL,
                            `unit` TEXT,
                            `is_completed` INTEGER NOT NULL,
                            `notes` TEXT,
                            `recorded_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_records_habit_id_date` ON `habit_records` (`habit_id`, `date`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_records_date` ON `habit_records` (`date`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val db = helperFactory.create(config).writableDatabase

        // 1. Insert v4 legacy data
        // Habit 1: Active Boolean habit created 2026-09-01 (epoch ms: 1788220800000 approx)
        val createdH1 = 1788220800000L // 2026-09-01T00:00:00Z
        db.execSQL(
            """
            INSERT INTO `habits` (`id`, `name`, `measurement_type`, `target_value`, `schedule_type`, `schedule_config`, `display_order`, `is_paused`, `is_archived`, `created_at`, `updated_at`)
            VALUES ('h_bool', 'Daily Yoga', 'BOOLEAN', 1.0, 'DAILY', '{}', 0, 0, 0, $createdH1, $createdH1)
            """.trimIndent()
        )

        // Habit 2: Quantitative habit, currently paused since updated_at
        val createdH2 = 1788220800000L
        val pausedAtH2 = 1788998400000L // 2026-09-10T00:00:00Z
        db.execSQL(
            """
            INSERT INTO `habits` (`id`, `name`, `measurement_type`, `target_value`, `unit`, `schedule_type`, `schedule_config`, `display_order`, `is_paused`, `is_archived`, `created_at`, `updated_at`)
            VALUES ('h_quant', 'Push-ups', 'COUNT', 50.0, 'reps', 'DAILY', '{}', 1, 1, 0, $createdH2, $pausedAtH2)
            """.trimIndent()
        )

        // Habit 3: Archived habit
        val createdH3 = 1788220800000L
        val archivedAtH3 = 1789430400000L // 2026-09-15T00:00:00Z
        db.execSQL(
            """
            INSERT INTO `habits` (`id`, `name`, `measurement_type`, `target_value`, `schedule_type`, `schedule_config`, `display_order`, `is_paused`, `is_archived`, `created_at`, `updated_at`)
            VALUES ('h_arch', 'Old Habit', 'BOOLEAN', 1.0, 'DAILY', '{}', 2, 0, 1, $createdH3, $archivedAtH3)
            """.trimIndent()
        )

        // Records for Habit 1:
        // rec1: Completed boolean record (is_completed = 1) -> should become DONE
        db.execSQL(
            """
            INSERT INTO `habit_records` (`id`, `habit_id`, `date`, `actual_value`, `target_value`, `measurement_type`, `is_completed`, `notes`, `recorded_at`)
            VALUES ('rec1', 'h_bool', '2026-09-01', 1.0, 1.0, 'BOOLEAN', 1, NULL, $createdH1)
            """.trimIndent()
        )
        // rec2: Explicit missed record with notes (is_completed = 0, actual = 0, notes = "Sick") -> should become MISSED
        db.execSQL(
            """
            INSERT INTO `habit_records` (`id`, `habit_id`, `date`, `actual_value`, `target_value`, `measurement_type`, `is_completed`, `notes`, `recorded_at`)
            VALUES ('rec2', 'h_bool', '2026-09-02', 0.0, 1.0, 'BOOLEAN', 0, 'Fever and headache', $createdH1 + 86400000)
            """.trimIndent()
        )
        // rec3: Empty zero-value record without notes (is_completed = 0, actual = 0, notes = null) -> should be DELETED to revert to virtual UNRECORDED
        db.execSQL(
            """
            INSERT INTO `habit_records` (`id`, `habit_id`, `date`, `actual_value`, `target_value`, `measurement_type`, `is_completed`, `notes`, `recorded_at`)
            VALUES ('rec3', 'h_bool', '2026-09-03', 0.0, 1.0, 'BOOLEAN', 0, NULL, $createdH1 + 172800000)
            """.trimIndent()
        )

        // Records for Habit 2:
        // rec4: Quantitative reached target (actual = 50, target = 50, is_completed = 0 in legacy) -> should become DONE
        db.execSQL(
            """
            INSERT INTO `habit_records` (`id`, `habit_id`, `date`, `actual_value`, `target_value`, `measurement_type`, `unit`, `is_completed`, `notes`, `recorded_at`)
            VALUES ('rec4', 'h_quant', '2026-09-01', 50.0, 50.0, 'COUNT', 'reps', 0, NULL, $createdH2)
            """.trimIndent()
        )
        // rec5: Quantitative exceeded target (actual = 60, target = 50) -> should become DONE
        db.execSQL(
            """
            INSERT INTO `habit_records` (`id`, `habit_id`, `date`, `actual_value`, `target_value`, `measurement_type`, `unit`, `is_completed`, `notes`, `recorded_at`)
            VALUES ('rec5', 'h_quant', '2026-09-02', 60.0, 50.0, 'COUNT', 'reps', 1, 'Great workout', $createdH2 + 86400000)
            """.trimIndent()
        )
        // rec6: Quantitative partial progress (actual = 25, target = 50, is_completed = 0) -> should become PARTIAL
        db.execSQL(
            """
            INSERT INTO `habit_records` (`id`, `habit_id`, `date`, `actual_value`, `target_value`, `measurement_type`, `unit`, `is_completed`, `notes`, `recorded_at`)
            VALUES ('rec6', 'h_quant', '2026-09-03', 25.0, 50.0, 'COUNT', 'reps', 0, 'Interrupted', $createdH2 + 172800000)
            """.trimIndent()
        )
        // rec7: Quantitative zero progress with note -> should become MISSED
        db.execSQL(
            """
            INSERT INTO `habit_records` (`id`, `habit_id`, `date`, `actual_value`, `target_value`, `measurement_type`, `unit`, `is_completed`, `notes`, `recorded_at`)
            VALUES ('rec7', 'h_quant', '2026-09-04', 0.0, 50.0, 'COUNT', 'reps', 0, 'Rest day', $createdH2 + 259200000)
            """.trimIndent()
        )

        // 2. Execute MIGRATION_4_5
        AppDatabase.MIGRATION_4_5.migrate(db)

        // 3. Verify Categories table exists
        val catCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='categories'")
        assertTrue("categories table must exist", catCursor.moveToFirst())
        catCursor.close()

        // 4. Verify Habit Pauses table exists and has initial pause for paused habit
        val pauseTableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='habit_pauses'")
        assertTrue("habit_pauses table must exist", pauseTableCursor.moveToFirst())
        pauseTableCursor.close()

        val pauseCursor = db.query("SELECT habit_id, start_date, end_date, reason FROM habit_pauses WHERE habit_id = 'h_quant'")
        assertTrue("Seeded pause must exist for h_quant", pauseCursor.moveToFirst())
        assertEquals("h_quant", pauseCursor.getString(0))
        assertEquals("2026-09-10", pauseCursor.getString(1)) // derived from updated_at
        assertTrue("end_date must be null for open-ended pause", pauseCursor.isNull(2))
        pauseCursor.close()

        // 5. Verify Habit Schedules table exists and has seeded initial schedules
        val schedCursor = db.query("SELECT habit_id, schedule_type, effective_from, effective_to FROM habit_schedules ORDER BY habit_id")
        assertTrue("Schedules must be seeded", schedCursor.moveToFirst())
        var seededCount = 0
        do {
            seededCount++
            assertTrue("effective_to must be null for initial schedule", schedCursor.isNull(3))
        } while (schedCursor.moveToNext())
        assertEquals(3, seededCount) // all 3 habits have initial schedule
        schedCursor.close()

        // 6. Verify Habits table alterations
        val habitCursor = db.query("SELECT id, start_date, category_id, is_currently_paused, archived_at FROM habits ORDER BY id")
        assertTrue(habitCursor.moveToFirst())
        do {
            val id = habitCursor.getString(0)
            val startDate = habitCursor.getString(1)
            val categoryId = if (habitCursor.isNull(2)) null else habitCursor.getString(2)
            val isCurrentlyPaused = habitCursor.getInt(3)
            val archivedAt = if (habitCursor.isNull(4)) null else habitCursor.getLong(4)

            assertEquals("2026-09-01", startDate)
            assertNull("category_id should be null", categoryId)

            when (id) {
                "h_bool" -> {
                    assertEquals(0, isCurrentlyPaused)
                    assertNull(archivedAt)
                }
                "h_quant" -> {
                    assertEquals(1, isCurrentlyPaused)
                    assertNull(archivedAt)
                }
                "h_arch" -> {
                    assertEquals(0, isCurrentlyPaused)
                    assertEquals(archivedAtH3, archivedAt)
                }
            }
        } while (habitCursor.moveToNext())
        habitCursor.close()

        // 7. Verify Habit Records Status Migration
        // rec1 -> DONE
        val r1Cursor = db.query("SELECT status, is_completed, actual_value FROM habit_records WHERE id = 'rec1'")
        assertTrue(r1Cursor.moveToFirst())
        assertEquals("DONE", r1Cursor.getString(0))
        r1Cursor.close()

        // rec2 -> MISSED (had notes)
        val r2Cursor = db.query("SELECT status, notes FROM habit_records WHERE id = 'rec2'")
        assertTrue(r2Cursor.moveToFirst())
        assertEquals("MISSED", r2Cursor.getString(0))
        assertEquals("Fever and headache", r2Cursor.getString(1))
        r2Cursor.close()

        // rec3 -> DELETED (empty zero-value record without notes)
        val r3Cursor = db.query("SELECT COUNT(*) FROM habit_records WHERE id = 'rec3'")
        assertTrue(r3Cursor.moveToFirst())
        assertEquals("rec3 must be deleted to revert to virtual UNRECORDED", 0, r3Cursor.getInt(0))
        r3Cursor.close()

        // rec4 -> DONE (actual 50 >= target 50)
        val r4Cursor = db.query("SELECT status, actual_value, target_value FROM habit_records WHERE id = 'rec4'")
        assertTrue(r4Cursor.moveToFirst())
        assertEquals("DONE", r4Cursor.getString(0))
        r4Cursor.close()

        // rec5 -> DONE (actual 60 >= target 50)
        val r5Cursor = db.query("SELECT status, actual_value, target_value FROM habit_records WHERE id = 'rec5'")
        assertTrue(r5Cursor.moveToFirst())
        assertEquals("DONE", r5Cursor.getString(0))
        r5Cursor.close()

        // rec6 -> PARTIAL (0 < actual 25 < target 50)
        val r6Cursor = db.query("SELECT status, actual_value, target_value FROM habit_records WHERE id = 'rec6'")
        assertTrue(r6Cursor.moveToFirst())
        assertEquals("PARTIAL", r6Cursor.getString(0))
        r6Cursor.close()

        // rec7 -> MISSED (actual 0 with note)
        val r7Cursor = db.query("SELECT status, notes FROM habit_records WHERE id = 'rec7'")
        assertTrue(r7Cursor.moveToFirst())
        assertEquals("MISSED", r7Cursor.getString(0))
        assertEquals("Rest day", r7Cursor.getString(1))
        r7Cursor.close()

        db.close()
    }
}
