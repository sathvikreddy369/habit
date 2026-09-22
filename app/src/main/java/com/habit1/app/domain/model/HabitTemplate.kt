package com.habit1.app.domain.model

import java.time.LocalTime

/**
 * Curated categories for habit templates.
 */
enum class HabitTemplateCategory(val displayName: String) {
    STUDENTS("Students"),
    ENGINEERING_STUDENTS("Engineering Students"),
    WORKING_PROFESSIONALS("Working Professionals"),
    MEDICAL_STUDENTS("Medical Students"),
    OLDER_ADULTS("Older Adults"),
    MID_AGED_ADULTS("Mid-aged Adults"),
    EVERYONE("Everyone")
}

/**
 * Immutable definition of a starting template for habit creation.
 * Does not enter Room database unless the user edits and saves it as a real habit.
 */
data class HabitTemplate(
    val id: String,
    val title: String,
    val description: String?,
    val category: HabitTemplateCategory,
    val measurementType: String,
    val targetValue: Double = 1.0,
    val unit: String? = null,
    val scheduleType: String = "DAILY",
    val scheduleConfig: String = "{}",
    val suggestedReminderTime: LocalTime? = null
)
