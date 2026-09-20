package com.habit1.app.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Standard utilities for date, time, and calendar operations.
 * Operates purely on java.time (JSR-310).
 */
object DateTimeUtils {

    val DATE_ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val TIME_ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun today(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zoneId)

    fun now(zoneId: ZoneId = ZoneId.systemDefault()): LocalTime = LocalTime.now(zoneId)

    fun formatDate(date: LocalDate): String = date.format(DATE_ISO_FORMATTER)

    fun parseDate(dateString: String): LocalDate = LocalDate.parse(dateString, DATE_ISO_FORMATTER)

    fun formatTime(time: LocalTime): String = time.format(TIME_ISO_FORMATTER)

    fun parseTime(timeString: String): LocalTime = LocalTime.parse(timeString, TIME_ISO_FORMATTER)

    fun daysBetween(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end)

    fun isToday(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
        date == today(zoneId)

    fun isFuture(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
        date.isAfter(today(zoneId))

    fun isPast(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
        date.isBefore(today(zoneId))

    fun toEpochMillis(instant: Instant): Long = instant.toEpochMilli()

    fun fromEpochMillis(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)

    fun toLocalDate(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        instant.atZone(zoneId).toLocalDate()
}
