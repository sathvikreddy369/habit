package com.habit1.app.domain

import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.mapper.EntityMappers.toEntity
import com.habit1.app.domain.model.DailyGoal
import com.habit1.app.domain.model.DailyReview
import com.habit1.app.domain.model.GoalSubtask
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class EntityMappersTest {

    private val nowInstant = Instant.parse("2026-09-20T12:00:00Z")

    @Test
    fun testHabitRoundtripBooleanDaily() {
        val habit = Habit(
            id = "h_bool",
            name = "Exercise",
            description = "Morning routine",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            reminderTime = LocalTime.of(7, 30),
            displayOrder = 1,
            isPaused = false,
            isArchived = false,
            createdAt = nowInstant,
            updatedAt = nowInstant
        )

        val entity = habit.toEntity()
        assertEquals("h_bool", entity.id)
        assertEquals("BOOLEAN", entity.measurementType)
        assertEquals(1.0, entity.targetValue, 0.001)
        assertNull(entity.unit)
        assertEquals("DAILY", entity.scheduleType)
        assertEquals("07:30", entity.reminderTime)

        val mappedBack = entity.toDomain()
        assertEquals(habit.id, mappedBack.id)
        assertEquals(habit.name, mappedBack.name)
        assertEquals(habit.measurement, mappedBack.measurement)
        assertEquals(habit.schedule, mappedBack.schedule)
        assertEquals(habit.reminderTime, mappedBack.reminderTime)
    }

    @Test
    fun testHabitRoundtripSpecificDaysCount() {
        val habit = Habit(
            id = "h_count",
            name = "Pushups",
            description = null,
            measurement = MeasurementType.Count(target = 50, unit = "reps"),
            schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            reminderTime = null,
            displayOrder = 2,
            createdAt = nowInstant,
            updatedAt = nowInstant
        )

        val entity = habit.toEntity()
        assertEquals("COUNT", entity.measurementType)
        assertEquals(50.0, entity.targetValue, 0.001)
        assertEquals("reps", entity.unit)
        assertEquals("SPECIFIC_DAYS", entity.scheduleType)

        val mappedBack = entity.toDomain()
        assertEquals(habit.measurement, mappedBack.measurement)
        assertEquals(habit.schedule, mappedBack.schedule)
    }

    @Test
    fun testHabitRoundtripIntervalQuantity() {
        val anchor = LocalDate.of(2026, 1, 1)
        val habit = Habit(
            id = "h_quant",
            name = "Water",
            measurement = MeasurementType.Quantity(target = 2.5, unit = "L"),
            schedule = HabitSchedule.Interval(everyNDays = 2, anchorDate = anchor),
            createdAt = nowInstant,
            updatedAt = nowInstant
        )

        val entity = habit.toEntity()
        assertEquals("QUANTITY", entity.measurementType)
        assertEquals(2.5, entity.targetValue, 0.001)
        assertEquals("L", entity.unit)
        assertEquals("INTERVAL", entity.scheduleType)

        val mappedBack = entity.toDomain()
        assertEquals(habit.measurement, mappedBack.measurement)
        assertEquals(habit.schedule, mappedBack.schedule)
    }

    @Test
    fun testHabitRecordRoundtrip() {
        val record = HabitRecord(
            id = "rec_1",
            habitId = "h_1",
            date = LocalDate.of(2026, 9, 20),
            actualValue = 2.5,
            targetValue = 2.5,
            measurementType = "QUANTITY",
            unit = "L",
            isCompleted = true,
            notes = "Drank 5 bottles of 500ml",
            recordedAt = nowInstant
        )

        val entity = record.toEntity()
        assertEquals("2026-09-20", entity.date)
        assertEquals(2.5, entity.actualValue, 0.001)
        assertEquals("QUANTITY", entity.measurementType)
        assertEquals("L", entity.unit)
        assertTrue(entity.isCompleted)

        val mappedBack = entity.toDomain()
        assertEquals(record, mappedBack)
    }

    @Test
    fun testDailyGoalWithSubtasksRoundtrip() {
        val goal = DailyGoal(
            id = "g_1",
            title = "Launch Phase 3",
            targetDate = LocalDate.of(2026, 9, 20),
            isCompleted = false,
            displayOrder = 0,
            subtasks = listOf(
                GoalSubtask("s_1", "g_1", "Implement models", true, 0, nowInstant),
                GoalSubtask("s_2", "g_1", "Write unit tests", false, 1, nowInstant)
            ),
            notes = "Milestone task",
            createdAt = nowInstant,
            updatedAt = nowInstant
        )

        val goalEntity = goal.toEntity()
        val subtaskEntities = goal.subtasks.map { it.toEntity() }
        val composite = DailyGoalWithSubtasks(goalEntity, subtaskEntities)

        val mappedBack = composite.toDomain()
        assertEquals(goal.id, mappedBack.id)
        assertEquals(goal.title, mappedBack.title)
        assertEquals(goal.targetDate, mappedBack.targetDate)
        assertEquals(2, mappedBack.subtasks.size)
        assertEquals("Implement models", mappedBack.subtasks[0].title)
        assertTrue(mappedBack.subtasks[0].isCompleted)
    }

    @Test
    fun testDailyReviewRoundtrip() {
        val review = DailyReview(
            date = LocalDate.of(2026, 9, 20),
            notes = "Excellent domain progress.",
            mood = "HAPPY",
            createdAt = nowInstant,
            updatedAt = nowInstant
        )

        val entity = review.toEntity()
        val mappedBack = entity.toDomain()
        assertEquals(review, mappedBack)
    }
}
