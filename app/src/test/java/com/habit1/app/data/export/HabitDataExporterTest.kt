package com.habit1.app.data.export

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class HabitDataExporterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Instant.parse("2026-03-01T12:00:00Z")

    private val habit1 = Habit(
        id = "h1",
        name = "Morning Run, \"5k\"",
        description = "Daily morning jog\nTrack pace",
        colorHex = "#FF5722",
        measurement = MeasurementType.Quantity(target = 5.0, unit = "km"),
        schedule = HabitSchedule.Daily,
        createdAt = now,
        updatedAt = now,
        startDate = LocalDate.of(2026, 1, 1),
        categoryId = "cat1"
    )

    private val habit2 = Habit(
        id = "h2",
        name = "Meditation",
        description = null,
        colorHex = "#2196F3",
        measurement = MeasurementType.Duration(targetMinutes = 15),
        schedule = HabitSchedule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        createdAt = now,
        updatedAt = now,
        startDate = LocalDate.of(2026, 1, 1),
        categoryId = null
    )

    private val habit3 = Habit(
        id = "h3",
        name = "Read Book",
        description = null,
        colorHex = "#4CAF50",
        measurement = MeasurementType.Count(target = 20, unit = "pages"),
        schedule = HabitSchedule.Daily,
        createdAt = now,
        updatedAt = now,
        startDate = LocalDate.of(2026, 1, 1),
        categoryId = null
    )

    private val recordsH1 = listOf(
        HabitRecord(
            id = "r1",
            habitId = "h1",
            date = LocalDate.of(2026, 3, 1),
            status = HabitRecordStatus.DONE,
            actualValue = 5.2,
            targetValue = 5.0,
            measurementType = "QUANTITY",
            unit = "km",
            notes = "Felt great, perfect weather!\nTempo run.",
            recordedAt = Instant.parse("2026-03-01T08:30:00Z")
        ),
        HabitRecord(
            id = "r2",
            habitId = "h1",
            date = LocalDate.of(2026, 3, 2),
            status = HabitRecordStatus.PARTIAL,
            actualValue = 3.0,
            targetValue = 5.0,
            measurementType = "QUANTITY",
            unit = "km",
            notes = "Short run due to rain",
            recordedAt = Instant.parse("2026-03-02T09:00:00Z")
        ),
        HabitRecord(
            id = "r3",
            habitId = "h1",
            date = LocalDate.of(2026, 3, 3),
            status = HabitRecordStatus.SKIPPED,
            actualValue = 0.0,
            targetValue = 5.0,
            measurementType = "QUANTITY",
            unit = "km",
            notes = "Rest day",
            recordedAt = Instant.parse("2026-03-03T10:00:00Z")
        )
    )

    private val recordsH2 = listOf(
        HabitRecord(
            id = "r4",
            habitId = "h2",
            date = LocalDate.of(2026, 3, 2),
            status = HabitRecordStatus.DONE,
            actualValue = 15.0,
            targetValue = 15.0,
            measurementType = "DURATION",
            unit = "min",
            notes = null,
            recordedAt = Instant.parse("2026-03-02T07:15:00Z")
        )
    )

    @Test
    fun exportToCsv_rfc4180EscapesCommasQuotesAndNewlinesCorrectly() {
        val out = ByteArrayOutputStream()
        val count = HabitDataExporter.exportToCsv(
            habits = listOf(habit1),
            recordsByHabit = mapOf("h1" to recordsH1),
            categoryNames = mapOf("cat1" to "Health & Fitness"),
            filter = ExportFilter(),
            outputStream = out
        )

        assertEquals(3, count)
        val csv = out.toString(Charsets.UTF_8.name())

        // Check header
        assertTrue(csv.startsWith("Habit ID,Habit Name,Category,Measurement Type"))

        // Row 1 should have escaped quotes: "Morning Run, ""5k"""
        assertTrue(csv.contains("\"Morning Run, \"\"5k\"\"\""))
        assertTrue(csv.contains("Health & Fitness"))
        // Check notes with comma and newline escaped
        assertTrue(csv.contains("\"Felt great, perfect weather!\nTempo run.\""))
    }

    @Test
    fun exportToCsv_filtersByHabitId() {
        val out = ByteArrayOutputStream()
        val count = HabitDataExporter.exportToCsv(
            habits = listOf(habit1, habit2),
            recordsByHabit = mapOf("h1" to recordsH1, "h2" to recordsH2),
            filter = ExportFilter(habitIds = setOf("h2")),
            outputStream = out
        )

        assertEquals(1, count)
        val csv = out.toString(Charsets.UTF_8.name())
        assertTrue(csv.contains("Meditation"))
        assertFalse(csv.contains("Morning Run"))
    }

    @Test
    fun exportToCsv_filtersByDateRange() {
        val out = ByteArrayOutputStream()
        val count = HabitDataExporter.exportToCsv(
            habits = listOf(habit1),
            recordsByHabit = mapOf("h1" to recordsH1),
            filter = ExportFilter(
                startDate = LocalDate.of(2026, 3, 2),
                endDate = LocalDate.of(2026, 3, 2)
            ),
            outputStream = out
        )

        assertEquals(1, count)
        val csv = out.toString(Charsets.UTF_8.name())
        assertTrue(csv.contains("2026-03-02"))
        assertFalse(csv.contains("2026-03-01"))
        assertFalse(csv.contains("2026-03-03"))
    }

    @Test
    fun exportToJson_validEnvelopeAndRecordPreservation() {
        val out = ByteArrayOutputStream()
        val count = HabitDataExporter.exportToJson(
            habits = listOf(habit1, habit3),
            recordsByHabit = mapOf("h1" to recordsH1),
            categoryNames = mapOf("cat1" to "Fitness"),
            filter = ExportFilter(),
            outputStream = out
        )

        assertEquals(3, count)
        val jsonString = out.toString(Charsets.UTF_8.name())
        val envelope = json.decodeFromString<HabitExportEnvelopeDto>(jsonString)

        assertEquals("Habit1", envelope.app)
        assertEquals(1, envelope.exportVersion)
        assertEquals(2, envelope.habitsCount)
        assertEquals(3, envelope.totalRecordsCount)

        val exportedH1 = envelope.habits.first { it.id == "h1" }
        assertEquals("Morning Run, \"5k\"", exportedH1.name)
        assertEquals("Fitness", exportedH1.categoryName)
        assertEquals("QUANTITY", exportedH1.measurementType)
        assertEquals(5.0, exportedH1.targetValue, 0.001)
        assertEquals("km", exportedH1.unit)
        assertEquals(3, exportedH1.records.size)

        val record1 = exportedH1.records[0]
        assertEquals("2026-03-01", record1.date)
        assertEquals("DONE", record1.status)
        assertEquals(5.2, record1.actualValue, 0.001)
        assertEquals(5.0, record1.targetValue, 0.001)
        assertTrue(record1.isCompleted)
        assertEquals("Felt great, perfect weather!\nTempo run.", record1.notes)

        val record2 = exportedH1.records[1]
        assertEquals("PARTIAL", record2.status)
        assertFalse(record2.isCompleted)
    }
}
