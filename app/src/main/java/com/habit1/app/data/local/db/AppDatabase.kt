package com.habit1.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habit1.app.data.local.db.dao.CategoryDao
import com.habit1.app.data.local.db.dao.DailyGoalDao
import com.habit1.app.data.local.db.dao.DailyReviewDao
import com.habit1.app.data.local.db.dao.HabitDao
import com.habit1.app.data.local.db.dao.HabitPauseDao
import com.habit1.app.data.local.db.dao.HabitRecordDao
import com.habit1.app.data.local.db.dao.HabitScheduleDao
import com.habit1.app.data.local.db.entity.CategoryEntity
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalHistoryAggregateEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitPauseEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.local.db.entity.HabitScheduleEntity

/**
 * Authoritative local SQLite database for the application.
 * WAL mode enabled for concurrent non-blocking reads and writes.
 */
@Database(
    entities = [
        HabitEntity::class,
        HabitRecordEntity::class,
        DailyGoalEntity::class,
        GoalSubtaskEntity::class,
        DailyReviewEntity::class,
        DailyGoalHistoryAggregateEntity::class,
        CategoryEntity::class,
        HabitPauseEntity::class,
        HabitScheduleEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitRecordDao(): HabitRecordDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun categoryDao(): CategoryDao
    abstract fun habitPauseDao(): HabitPauseDao
    abstract fun habitScheduleDao(): HabitScheduleDao

    companion object {
        const val DATABASE_NAME = "habit_app.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_goal_history_aggregates` (
                        `date` TEXT NOT NULL,
                        `completed_count` INTEGER NOT NULL,
                        `total_count` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `color_hex` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_goals` ADD COLUMN `reminder_time` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create categories table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `color_hex` TEXT DEFAULT NULL,
                        `icon` TEXT DEFAULT NULL,
                        `display_order` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_display_order` ON `categories` (`display_order`)")

                // 2. Create habit_pauses table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `habit_pauses` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `habit_id` TEXT NOT NULL,
                        `start_date` TEXT NOT NULL,
                        `end_date` TEXT DEFAULT NULL,
                        `reason` TEXT DEFAULT NULL,
                        FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_pauses_habit_id_start_date` ON `habit_pauses` (`habit_id`, `start_date`)")

                // 3. Create habit_schedules table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `habit_schedules` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `habit_id` TEXT NOT NULL,
                        `schedule_type` TEXT NOT NULL,
                        `schedule_config` TEXT NOT NULL,
                        `effective_from` TEXT NOT NULL,
                        `effective_to` TEXT DEFAULT NULL,
                        FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_schedules_habit_id_effective_from` ON `habit_schedules` (`habit_id`, `effective_from`)")

                // 4. Alter habits table: add start_date, category_id, is_currently_paused, archived_at
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `start_date` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `category_id` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `is_currently_paused` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `archived_at` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_category_id` ON `habits` (`category_id`)")

                // Populate start_date from created_at
                db.execSQL("UPDATE `habits` SET `start_date` = date(`created_at` / 1000, 'unixepoch') WHERE `start_date` = ''")
                // Populate is_currently_paused from is_paused
                db.execSQL("UPDATE `habits` SET `is_currently_paused` = `is_paused`")
                // Populate archived_at for currently archived habits
                db.execSQL("UPDATE `habits` SET `archived_at` = `updated_at` WHERE `is_archived` = 1")

                // Seed initial habit_schedules for all existing habits
                db.execSQL(
                    """
                    INSERT INTO `habit_schedules` (`id`, `habit_id`, `schedule_type`, `schedule_config`, `effective_from`, `effective_to`)
                    SELECT ('sched_' || `id`), `id`, `schedule_type`, `schedule_config`, `start_date`, NULL FROM `habits`
                    """.trimIndent()
                )

                // Seed initial habit_pauses for currently paused habits
                db.execSQL(
                    """
                    INSERT INTO `habit_pauses` (`id`, `habit_id`, `start_date`, `end_date`, `reason`)
                    SELECT ('pause_' || `id`), `id`, date(`updated_at` / 1000, 'unixepoch'), NULL, 'Migrated pause'
                    FROM `habits` WHERE `is_paused` = 1
                    """.trimIndent()
                )

                // 5. Alter habit_records table: add status column
                db.execSQL("ALTER TABLE `habit_records` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'DONE'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_records_habit_id_status` ON `habit_records` (`habit_id`, `status`)")

                // Data migration for habit_records:
                // Quantitative PARTIAL: actual > 0 and actual < target and is_completed = 0
                db.execSQL(
                    """
                    UPDATE `habit_records` 
                    SET `status` = 'PARTIAL' 
                    WHERE `actual_value` > 0 AND `actual_value` < `target_value` AND `is_completed` = 0
                    """.trimIndent()
                )

                // DONE: actual >= target or is_completed = 1
                db.execSQL(
                    """
                    UPDATE `habit_records` 
                    SET `status` = 'DONE' 
                    WHERE `actual_value` >= `target_value` OR `is_completed` = 1
                    """.trimIndent()
                )

                // Explicit MISSED: actual = 0 and is_completed = 0 and notes exist
                db.execSQL(
                    """
                    UPDATE `habit_records` 
                    SET `status` = 'MISSED' 
                    WHERE `is_completed` = 0 AND `actual_value` = 0 AND `notes` IS NOT NULL AND TRIM(`notes`) != ''
                    """.trimIndent()
                )

                // Delete empty zero records (is_completed = 0, actual_value = 0, no notes) -> revert to virtual UNRECORDED
                db.execSQL(
                    """
                    DELETE FROM `habit_records` 
                    WHERE `is_completed` = 0 AND `actual_value` = 0 AND (`notes` IS NULL OR TRIM(`notes`) = '')
                    """.trimIndent()
                )
            }
        }

        fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Explicitly enforce foreign key constraints
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .build()
        }

        fun buildInMemoryDatabase(context: Context): AppDatabase {
            val executor = kotlinx.coroutines.Dispatchers.Unconfined.let {
                java.util.concurrent.Executor { command -> command.run() }
            }
            return Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .allowMainThreadQueries()
                .setQueryExecutor(executor)
                .setTransactionExecutor(executor)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .build()
        }
    }
}
