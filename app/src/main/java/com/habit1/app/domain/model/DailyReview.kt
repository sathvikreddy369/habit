package com.habit1.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Pure domain representation of an optional daily reflection note.
 */
data class DailyReview(
    val date: LocalDate,
    val notes: String? = null,
    val mood: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
