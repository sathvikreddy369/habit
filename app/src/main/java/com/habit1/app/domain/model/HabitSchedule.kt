package com.habit1.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Habit scheduling rules defining when a habit is expected.
 * Operates purely on civil calendar dates (LocalDate) and days of week.
 */
sealed interface HabitSchedule {
    val scheduleType: String

    /**
     * Expected every single civil calendar day.
     */
    data object Daily : HabitSchedule {
        override val scheduleType: String = TYPE_NAME
        const val TYPE_NAME = "DAILY"
    }

    /**
     * Expected on specific weekdays (e.g., Monday, Wednesday, Friday).
     */
    data class SpecificDays(
        val days: Set<DayOfWeek>
    ) : HabitSchedule {
        override val scheduleType: String = TYPE_NAME
        init {
            require(days.isNotEmpty()) { "SpecificDays schedule must specify at least one day of week" }
        }
        companion object {
            const val TYPE_NAME = "SPECIFIC_DAYS"
        }
    }

    /**
     * Expected every N days from a fixed anchor civil date.
     * (e.g., every 2 days starting 2026-01-01).
     */
    data class Interval(
        val everyNDays: Int,
        val anchorDate: LocalDate
    ) : HabitSchedule {
        override val scheduleType: String = TYPE_NAME
        init {
            require(everyNDays >= 2) { "Interval frequency must be at least 2 days" }
        }
        companion object {
            const val TYPE_NAME = "INTERVAL"
        }
    }
}
