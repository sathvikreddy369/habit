package com.habit1.app.domain.model

/**
 * Authoritative domain status for a recorded habit on a civil date.
 * (The 5th state, UNRECORDED, is the virtual default represented by the absence of a record).
 */
enum class HabitRecordStatus {
    DONE,
    PARTIAL,
    SKIPPED,
    MISSED,
    UNRECORDED;

    companion object {
        fun fromString(value: String): HabitRecordStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: DONE
        }
    }
}
