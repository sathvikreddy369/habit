package com.habit1.app.domain.model

import java.time.Instant

/**
 * Pure domain representation of a habit category.
 */
data class Category(
    val id: String,
    val name: String,
    val colorHex: String? = null,
    val icon: String? = null,
    val displayOrder: Int = 0,
    val createdAt: Instant
)
