package com.habit1.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.habit1.app.data.local.db.dao.DailyGoalDao
import com.habit1.app.data.local.db.dao.DailyReviewDao
import com.habit1.app.data.local.db.dao.HabitDao
import com.habit1.app.data.local.db.dao.HabitRecordDao
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity

import androidx.room.migration.Migration
import com.habit1.app.data.local.db.entity.DailyGoalHistoryAggregateEntity

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
        DailyGoalHistoryAggregateEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitRecordDao(): HabitRecordDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun dailyReviewDao(): DailyReviewDao

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

        fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
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
                .addMigrations(MIGRATION_1_2)
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
