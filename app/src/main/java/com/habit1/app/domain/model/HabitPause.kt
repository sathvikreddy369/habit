package com.habit1.app.domain.model

import java.time.LocalDate

/**
 * Pure domain representation of a temporal pause interval.
 */
data class HabitPause(
    val id: String,
    val habitId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val reason: String? = null
) {
    /**
     * Checks if this pause interval covers the given civil date (startDate <= date <= (endDate ?: today/infinity)).
     */
    fun covers(date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        val end = endDate ?: return true
        return !date.isAfter(end)
    }
}
