package com.habit1.app.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.dao.HabitDao
import com.habit1.app.data.local.db.dao.HabitRecordDao
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Tests Room database behavior, integrity, and query performance
 * under realistic long-term usage (years of historical data).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LongTermUsageDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var recordDao: HabitRecordDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitDao = database.habitDao()
        recordDao = database.habitRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testMultiYearHistoricalDataScaleAndQueryPerformance() = runBlocking {
        val now = System.currentTimeMillis()
        val habitCount = 10
        val years = 3
        val totalDays = years * 365

        // Create 10 active habits
        val habits = (1..habitCount).map { i ->
            HabitEntity(
                id = "habit_$i",
                name = "Habit #$i",
                measurementType = if (i % 2 == 0) "BOOLEAN" else "COUNT",
                targetValue = if (i % 2 == 0) 1.0 else 50.0,
                unit = if (i % 2 == 0) null else "reps",
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                displayOrder = i,
                createdAt = now,
                updatedAt = now
            )
        }
        habits.forEach { habitDao.insert(it) }

        // Generate 3 years of daily records (~10,950 rows)
        val startDate = LocalDate.of(2023, 1, 1)
        val bulkRecords = ArrayList<HabitRecordEntity>(totalDays * habitCount)

        for (dayOffset in 0 until totalDays) {
            val currentDate = startDate.plusDays(dayOffset.toLong()).toString()
            for (habitIndex in 1..habitCount) {
                // User completed every other day
                val isCompleted = (dayOffset + habitIndex) % 2 == 0
                val actual = if (isCompleted) (if (habitIndex % 2 == 0) 1.0 else 50.0) else 0.0

                bulkRecords.add(
                    HabitRecordEntity(
                        id = "rec_${habitIndex}_$dayOffset",
                        habitId = "habit_$habitIndex",
                        date = currentDate,
                        actualValue = actual,
                        targetValue = if (habitIndex % 2 == 0) 1.0 else 50.0,
                        measurementType = if (habitIndex % 2 == 0) "BOOLEAN" else "COUNT",
                        unit = if (habitIndex % 2 == 0) null else "reps",
                        isCompleted = isCompleted,
                        recordedAt = now
                    )
                )
            }
        }

        // Bulk insert records
        recordDao.upsertAll(bulkRecords)

        val totalInserted = recordDao.totalRecordsCount()
        assertEquals(totalDays * habitCount, totalInserted)
        assertTrue(totalInserted >= 10000)

        // 1. Benchmark: Date-windowed query for a single month (e.g. 2024-06-01 to 2024-06-30)
        val queryStart = System.nanoTime()
        val monthRecords = recordDao.getRecordsForDateRange("2024-06-01", "2024-06-30")
        val queryDurationMs = (System.nanoTime() - queryStart) / 1_000_000.0

        // 30 days * 10 habits = 300 records
        assertEquals(300, monthRecords.size)

        // Query execution on indexed date column should be extremely fast (typically < 15ms even in JVM in-memory emulation)
        assertTrue("Date-window query took ${queryDurationMs}ms, expected fast execution", queryDurationMs < 100.0)

        // 2. Query single habit's complete history over 3 years
        val habit1History = recordDao.getRecordsForHabit("habit_1")
        assertEquals(totalDays, habit1History.size)

        // 3. Query single habit's range
        val habit1Range = recordDao.getRecordsForHabitInRange("habit_1", "2024-01-01", "2024-12-31")
        assertEquals(366, habit1Range.size) // 2024 was a leap year (366 days)

        // 4. Test cascading deletion under large dataset
        habitDao.deleteById("habit_1")
        val remainingAfterDeletion = recordDao.totalRecordsCount()
        assertEquals(totalInserted - totalDays, remainingAfterDeletion)
    }
}
