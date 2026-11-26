package com.habit1.app.domain.model

import java.time.LocalDate

/**
 * Pure domain representation of a temporal schedule version.
 */
data class HabitScheduleVersion(
    val id: String,
    val habitId: String,
    val schedule: HabitSchedule,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate? = null
) {
    /**
     * Checks if this schedule version was effective on the given civil date.
     */
    fun isEffectiveOn(date: LocalDate): Boolean {
        if (date.isBefore(effectiveFrom)) return false
        val end = effectiveTo ?: return true
        return !date.isAfter(end)
    }
}
