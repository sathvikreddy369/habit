package com.habit1.app.domain.mapper

import com.habit1.app.domain.model.HabitSchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate

@Serializable
private data class SpecificDaysConfig(val days: List<Int>)

@Serializable
private data class IntervalConfig(val everyNDays: Int, val anchorDate: String)

/**
 * Deterministic serializer and deserializer for HabitSchedule configuration strings.
 */
object ScheduleConfigSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun serialize(schedule: HabitSchedule): Pair<String, String> {
        return when (schedule) {
            is HabitSchedule.Daily -> {
                Pair(HabitSchedule.Daily.TYPE_NAME, "{}")
            }
            is HabitSchedule.SpecificDays -> {
                val daysList = schedule.days.map { it.value }.sorted()
                val config = json.encodeToString(SpecificDaysConfig.serializer(), SpecificDaysConfig(daysList))
                Pair(HabitSchedule.SpecificDays.TYPE_NAME, config)
            }
            is HabitSchedule.Interval -> {
                val config = json.encodeToString(
                    IntervalConfig.serializer(),
                    IntervalConfig(schedule.everyNDays, schedule.anchorDate.toString())
                )
                Pair(HabitSchedule.Interval.TYPE_NAME, config)
            }
        }
    }

    fun deserialize(scheduleType: String, scheduleConfig: String, fallbackAnchorDate: LocalDate = LocalDate.now()): HabitSchedule {
        return when (scheduleType) {
            HabitSchedule.SpecificDays.TYPE_NAME -> {
                try {
                    val parsed = json.decodeFromString(SpecificDaysConfig.serializer(), scheduleConfig)
                    val days = parsed.days.mapNotNull {
                        if (it in 1..7) DayOfWeek.of(it) else null
                    }.toSet()
                    if (days.isNotEmpty()) HabitSchedule.SpecificDays(days) else HabitSchedule.Daily
                } catch (e: Exception) {
                    HabitSchedule.Daily
                }
            }
            HabitSchedule.Interval.TYPE_NAME -> {
                try {
                    val parsed = json.decodeFromString(IntervalConfig.serializer(), scheduleConfig)
                    val anchor = try {
                        LocalDate.parse(parsed.anchorDate)
                    } catch (e: Exception) {
                        fallbackAnchorDate
                    }
                    val frequency = if (parsed.everyNDays >= 2) parsed.everyNDays else 2
                    HabitSchedule.Interval(frequency, anchor)
                } catch (e: Exception) {
                    HabitSchedule.Daily
                }
            }
            else -> HabitSchedule.Daily
        }
    }
}
