package com.habit1.app.domain.reminder

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

sealed interface SnoozeResult {
    data class Scheduled(
        val triggerTime: LocalTime,
        val triggerEpochMillis: Long
    ) : SnoozeResult

    data object DisallowedPastBoundary : SnoozeResult
}

class SnoozeCalculator {

    companion object {
        val SNOOZE_BOUNDARY: LocalTime = LocalTime.of(23, 50)
        val ONE_HOUR_BEFORE_MIDNIGHT: LocalTime = LocalTime.of(23, 0)
    }

    /**
     * Calculates the snooze trigger instant for today.
     * Rules:
     * - Disallowed at or after 23:50.
     * - If at least 1 hour remains before midnight (now <= 23:00) AND now + 60min <= 23:50: snooze 60 minutes.
     * - If less than 1 hour remains before midnight (now > 23:00) OR now + 60min > 23:50: clamp to 23:50 today.
     * - Strictly prohibited from crossing midnight into the next civil date.
     */
    fun calculateSnooze(
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SnoozeResult {
        val localTime = now.toLocalTime()

        // At or after 23:50: disallow snoozing
        if (!localTime.isBefore(SNOOZE_BOUNDARY)) {
            return SnoozeResult.DisallowedPastBoundary
        }

        val sixtyMinutesLater = localTime.plusMinutes(60)

        val targetTime = if (!localTime.isAfter(ONE_HOUR_BEFORE_MIDNIGHT) && !sixtyMinutesLater.isAfter(SNOOZE_BOUNDARY) && sixtyMinutesLater.isAfter(localTime)) {
            sixtyMinutesLater
        } else {
            SNOOZE_BOUNDARY
        }

        val triggerDateTime = LocalDateTime.of(now.toLocalDate(), targetTime)
        val triggerEpochMillis = triggerDateTime.atZone(zoneId).toInstant().toEpochMilli()
        return SnoozeResult.Scheduled(targetTime, triggerEpochMillis)
    }
}
