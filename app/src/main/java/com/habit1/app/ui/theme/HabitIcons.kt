package com.habit1.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated library of 36 standard vector habit and category icons across health,
 * fitness, learning, mindfulness, work, chores and lifestyle (Decision 6.3).
 */
object HabitIcons {

    data class IconItem(
        val key: String,
        val label: String,
        val category: String,
        val icon: ImageVector
    )

    /**
     * Exact 36 curated standard vector icons.
     */
    val ICONS: List<IconItem> = listOf(
        // Health & Fitness (1 - 10)
        IconItem("fitness", "Workout", "Health & Fitness", Icons.Default.FitnessCenter),
        IconItem("run", "Running", "Health & Fitness", Icons.Default.DirectionsRun),
        IconItem("walk", "Walking", "Health & Fitness", Icons.Default.DirectionsWalk),
        IconItem("bike", "Cycling", "Health & Fitness", Icons.Default.DirectionsBike),
        IconItem("water", "Hydration", "Health & Fitness", Icons.Default.LocalDrink),
        IconItem("nutrition", "Nutrition", "Health & Fitness", Icons.Default.Restaurant),
        IconItem("sleep", "Sleep", "Health & Fitness", Icons.Default.Bedtime),
        IconItem("health", "Heart", "Health & Fitness", Icons.Default.Favorite),
        IconItem("meditation", "Meditation", "Health & Fitness", Icons.Default.SelfImprovement),
        IconItem("spa", "Wellness", "Health & Fitness", Icons.Default.Spa),

        // Learning & Mind (11 - 16)
        IconItem("book", "Reading", "Learning & Mind", Icons.Default.MenuBook),
        IconItem("study", "Education", "Learning & Mind", Icons.Default.School),
        IconItem("brain", "Mindfulness", "Learning & Mind", Icons.Default.Psychology),
        IconItem("journal", "Writing", "Learning & Mind", Icons.Default.Edit),
        IconItem("idea", "Creativity", "Learning & Mind", Icons.Default.Lightbulb),
        IconItem("language", "Language", "Learning & Mind", Icons.Default.Translate),

        // Work & Productivity (17 - 24)
        IconItem("work", "Career", "Work & Productivity", Icons.Default.Work),
        IconItem("code", "Tech", "Work & Productivity", Icons.Default.Computer),
        IconItem("task", "Tasks", "Work & Productivity", Icons.Default.CheckCircle),
        IconItem("time", "Timer", "Work & Productivity", Icons.Default.Schedule),
        IconItem("money", "Finance", "Work & Productivity", Icons.Default.AttachMoney),
        IconItem("call", "Communication", "Work & Productivity", Icons.Default.Phone),
        IconItem("mail", "Email", "Work & Productivity", Icons.Default.Email),
        IconItem("target", "Goals", "Work & Productivity", Icons.Default.Flag),

        // Home & Chores (25 - 30)
        IconItem("home", "Home", "Home & Chores", Icons.Default.Home),
        IconItem("clean", "Cleaning", "Home & Chores", Icons.Default.CleaningServices),
        IconItem("groceries", "Shopping", "Home & Chores", Icons.Default.ShoppingCart),
        IconItem("laundry", "Laundry", "Home & Chores", Icons.Default.LocalLaundryService),
        IconItem("pets", "Pets", "Home & Chores", Icons.Default.Pets),
        IconItem("garden", "Plants", "Home & Chores", Icons.Default.Eco),

        // Lifestyle & Wellbeing (31 - 36)
        IconItem("music", "Music", "Lifestyle", Icons.Default.MusicNote),
        IconItem("art", "Art", "Lifestyle", Icons.Default.Brush),
        IconItem("star", "Star", "Lifestyle", Icons.Default.Star),
        IconItem("morning", "Morning", "Lifestyle", Icons.Default.WbSunny),
        IconItem("evening", "Night", "Lifestyle", Icons.Default.NightsStay),
        IconItem("coffee", "Coffee", "Lifestyle", Icons.Default.LocalCafe)
    )

    private val ICONS_BY_KEY = ICONS.associateBy { it.key }

    /**
     * Resolves an icon key to its corresponding vector icon, or null if the key is null or an emoji.
     */
    fun getVectorIcon(key: String?): ImageVector? {
        if (key == null) return null
        return ICONS_BY_KEY[key]?.icon
    }

    /**
     * Determines whether the given string is a Unicode emoji rather than a standard vector icon key.
     */
    fun isEmoji(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (ICONS_BY_KEY.containsKey(text)) return false
        // Single emoji or grapheme cluster check
        return text.codePoints().anyMatch { Character.isSurrogate(it.toChar()) || it > 0x2000 }
    }
}
