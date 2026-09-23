package com.habit1.app.domain.template

import com.habit1.app.domain.model.HabitTemplate
import com.habit1.app.domain.model.HabitTemplateCategory
import java.time.LocalTime

/**
 * Curated, in-memory catalog of habit templates.
 * Provides practical, realistic starter definitions without database overhead.
 */
object HabitTemplatesProvider {

    val allTemplates: List<HabitTemplate> = listOf(
        // --- 1. Students ---
        HabitTemplate(
            id = "template_student_study",
            title = "Study",
            description = "Focused course study time",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "DURATION",
            targetValue = 60.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_student_read",
            title = "Read Textbook",
            description = "Read assigned course chapters",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "COUNT",
            targetValue = 15.0,
            unit = "pages"
        ),
        HabitTemplate(
            id = "template_student_review_notes",
            title = "Review Notes",
            description = "Daily recap of class notes",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_student_practice_problems",
            title = "Practice Problems",
            description = "Solve assignment and practice problems",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "COUNT",
            targetValue = 10.0,
            unit = "problems"
        ),
        HabitTemplate(
            id = "template_student_drink_water",
            title = "Drink Water",
            description = "Maintain daily hydration while studying",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "QUANTITY",
            targetValue = 2.0,
            unit = "L"
        ),
        HabitTemplate(
            id = "template_student_exercise",
            title = "Exercise",
            description = "Physical workout or campus run",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_student_sleep_on_time",
            title = "Sleep on Time",
            description = "Consistent bedtime for academic recovery",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            suggestedReminderTime = LocalTime.of(22, 30)
        ),

        // --- 2. Engineering Students ---
        HabitTemplate(
            id = "template_eng_dsa",
            title = "DSA Practice",
            description = "Data structures and algorithm practice",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "COUNT",
            targetValue = 2.0,
            unit = "problems"
        ),
        HabitTemplate(
            id = "template_eng_coding",
            title = "Coding Practice",
            description = "Hands-on coding or open-source contribution",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "DURATION",
            targetValue = 60.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_eng_leetcode",
            title = "LeetCode / Problem Solving",
            description = "Competitive and interview problem solving",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "COUNT",
            targetValue = 3.0,
            unit = "problems"
        ),
        HabitTemplate(
            id = "template_eng_cs_fundamentals",
            title = "CS Fundamentals Revision",
            description = "OS, DBMS, Computer Networks, or OOP review",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_eng_project_work",
            title = "Project Work",
            description = "Building personal or capstone software project",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "DURATION",
            targetValue = 45.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_eng_tech_reading",
            title = "Technical Reading",
            description = "Articles, documentation, or architecture whitepapers",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "COUNT",
            targetValue = 1.0,
            unit = "articles"
        ),

        // --- 3. Working Professionals ---
        HabitTemplate(
            id = "template_work_deep_work",
            title = "Deep Work",
            description = "Uninterrupted focus session without Slack/email",
            category = HabitTemplateCategory.WORKING_PROFESSIONALS,
            measurementType = "DURATION",
            targetValue = 90.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_work_plan_tomorrow",
            title = "Plan Tomorrow",
            description = "Outline top priorities for the next working day",
            category = HabitTemplateCategory.WORKING_PROFESSIONALS,
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            suggestedReminderTime = LocalTime.of(17, 30)
        ),
        HabitTemplate(
            id = "template_work_exercise",
            title = "Exercise",
            description = "Gym, running, or home workout",
            category = HabitTemplateCategory.WORKING_PROFESSIONALS,
            measurementType = "DURATION",
            targetValue = 40.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_work_drink_water",
            title = "Drink Water",
            description = "Desk hydration throughout work hours",
            category = HabitTemplateCategory.WORKING_PROFESSIONALS,
            measurementType = "QUANTITY",
            targetValue = 2.5,
            unit = "L"
        ),
        HabitTemplate(
            id = "template_work_learning",
            title = "Learning",
            description = "Skill development, courses, or industry research",
            category = HabitTemplateCategory.WORKING_PROFESSIONALS,
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_work_sleep_routine",
            title = "Sleep Routine",
            description = "Consistent bedtime for high cognitive energy",
            category = HabitTemplateCategory.WORKING_PROFESSIONALS,
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            suggestedReminderTime = LocalTime.of(23, 0)
        ),

        // --- 4. Medical / Healthcare Students ---
        HabitTemplate(
            id = "template_med_study",
            title = "Study",
            description = "Anatomy, physiology, pathology, or pharmacology study",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "DURATION",
            targetValue = 90.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_med_flashcards",
            title = "Flashcard Revision",
            description = "Spaced repetition flashcards (Anki/quiz)",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "COUNT",
            targetValue = 50.0,
            unit = "cards"
        ),
        HabitTemplate(
            id = "template_med_clinical_reading",
            title = "Clinical Reading",
            description = "Medical journals, guidelines, or case reports",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "COUNT",
            targetValue = 1.0,
            unit = "cases"
        ),
        HabitTemplate(
            id = "template_med_hydration",
            title = "Hydration",
            description = "Stay hydrated during rounds and hospital shifts",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "QUANTITY",
            targetValue = 2.5,
            unit = "L"
        ),
        HabitTemplate(
            id = "template_med_sleep",
            title = "Sleep Routine",
            description = "Ensure adequate restorative rest",
            category = HabitTemplateCategory.STUDENTS,
            measurementType = "BOOLEAN",
            targetValue = 1.0
        ),

        // --- 5. Older Adults ---
        HabitTemplate(
            id = "template_older_walk",
            title = "Daily Walk",
            description = "Comfortable outdoor or park stroll",
            category = HabitTemplateCategory.OLDER_ADULTS,
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_older_stretch",
            title = "Gentle Stretch",
            description = "Joint mobility and flexibility exercises",
            category = HabitTemplateCategory.OLDER_ADULTS,
            measurementType = "DURATION",
            targetValue = 15.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_older_drink_water",
            title = "Drink Water",
            description = "Regular water intake across the day",
            category = HabitTemplateCategory.OLDER_ADULTS,
            measurementType = "COUNT",
            targetValue = 6.0,
            unit = "glasses"
        ),
        HabitTemplate(
            id = "template_older_read",
            title = "Reading",
            description = "Newspaper, book, or magazine reading",
            category = HabitTemplateCategory.OLDER_ADULTS,
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_older_social",
            title = "Social Connection",
            description = "Call family, visit neighbor, or meet friends",
            category = HabitTemplateCategory.OLDER_ADULTS,
            measurementType = "BOOLEAN",
            targetValue = 1.0
        ),

        // --- 6. Mid-aged Adults ---
        HabitTemplate(
            id = "template_mid_exercise",
            title = "Daily Exercise",
            description = "Aerobic or resistance training",
            category = HabitTemplateCategory.MID_AGED_ADULTS,
            measurementType = "DURATION",
            targetValue = 45.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_mid_walk",
            title = "Brisk Walk",
            description = "Brisk steps for cardiovascular health",
            category = HabitTemplateCategory.MID_AGED_ADULTS,
            measurementType = "COUNT",
            targetValue = 6000.0,
            unit = "steps"
        ),
        HabitTemplate(
            id = "template_mid_hydration",
            title = "Hydration",
            description = "Daily water consumption",
            category = HabitTemplateCategory.MID_AGED_ADULTS,
            measurementType = "QUANTITY",
            targetValue = 2.0,
            unit = "L"
        ),
        HabitTemplate(
            id = "template_mid_reading",
            title = "Reading",
            description = "Non-fiction or recreational reading",
            category = HabitTemplateCategory.MID_AGED_ADULTS,
            measurementType = "DURATION",
            targetValue = 20.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_mid_planning",
            title = "Planning & Review",
            description = "Organize week, finances, or family schedule",
            category = HabitTemplateCategory.MID_AGED_ADULTS,
            measurementType = "BOOLEAN",
            targetValue = 1.0
        ),

        // --- 7. Everyone ---
        HabitTemplate(
            id = "template_all_drink_water",
            title = "Drink Water",
            description = "Daily hydration target",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "QUANTITY",
            targetValue = 2.0,
            unit = "L"
        ),
        HabitTemplate(
            id = "template_all_exercise",
            title = "Daily Exercise",
            description = "Movement, cardio, or strength training",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_all_read",
            title = "Read 20 min",
            description = "Daily book or article reading",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "DURATION",
            targetValue = 20.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_all_walk",
            title = "Daily Walk",
            description = "Outdoor walk or step goal",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "COUNT",
            targetValue = 5000.0,
            unit = "steps"
        ),
        HabitTemplate(
            id = "template_all_meditation",
            title = "Mindful Meditation",
            description = "Quiet breathing and mindfulness practice",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "DURATION",
            targetValue = 10.0,
            unit = "min"
        ),
        HabitTemplate(
            id = "template_all_journaling",
            title = "Journaling",
            description = "Daily written reflection or gratitude notes",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "BOOLEAN",
            targetValue = 1.0
        ),
        HabitTemplate(
            id = "template_all_sleep_on_time",
            title = "Sleep on Time",
            description = "Consistent restorative bedtime routine",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            suggestedReminderTime = LocalTime.of(22, 30)
        ),
        HabitTemplate(
            id = "template_all_limit_screen_time",
            title = "Limit Screen Time",
            description = "Screen-free evening hour before bed",
            category = HabitTemplateCategory.EVERYONE,
            measurementType = "BOOLEAN",
            targetValue = 1.0
        )
    )

    fun getTemplateById(id: String): HabitTemplate? = allTemplates.find { it.id == id }

    fun filterTemplates(
        category: HabitTemplateCategory?,
        searchQuery: String
    ): List<HabitTemplate> {
        val query = searchQuery.trim().lowercase()
        return allTemplates.filter { template ->
            val matchesCategory = category == null || template.category == category
            val matchesSearch = query.isEmpty() ||
                template.title.lowercase().contains(query) ||
                (template.description?.lowercase()?.contains(query) == true) ||
                template.category.displayName.lowercase().contains(query)
            matchesCategory && matchesSearch
        }
    }
}
