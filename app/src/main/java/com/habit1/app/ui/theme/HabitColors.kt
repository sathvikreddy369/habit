package com.habit1.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated palette and utilities for habit-specific custom colors.
 */
object HabitColors {

    /**
     * Default habit accent color (Peach / Coral, matching reference design).
     */
    val DEFAULT_COLOR_HEX = "#F08A6C"
    val DEFAULT_COLOR = Color(0xFFF08A6C)

    /**
     * Curated palette colors across multiple tonal families (Warm, Cool, Vibrant, Earthy, Monochrome)
     * giving users a wide variety of colors to choose from.
     */
    val PALETTE: List<Pair<String, Color>> = listOf(
        // Warm & Corals
        Pair("#F08A6C", Color(0xFFF08A6C)), // Peach / Coral (Default)
        Pair("#FF7043", Color(0xFFFF7043)), // Deep Orange
        Pair("#FFA726", Color(0xFFFFA726)), // Amber
        Pair("#FFCA28", Color(0xFFFFCA28)), // Golden Yellow
        Pair("#E57373", Color(0xFFE57373)), // Soft Red
        Pair("#EF5350", Color(0xFFEF5350)), // Crimson Red
        Pair("#EC407A", Color(0xFFEC407A)), // Rose Pink
        Pair("#F48FB1", Color(0xFFF48FB1)), // Soft Blush Pink

        // Purples & Violets
        Pair("#AB47BC", Color(0xFFAB47BC)), // Orchid Purple
        Pair("#7E57C2", Color(0xFF7E57C2)), // Deep Purple
        Pair("#9575CD", Color(0xFF9575CD)), // Lavender
        Pair("#5C6BC0", Color(0xFF5C6BC0)), // Indigo

        // Blues & Cyans
        Pair("#3F51B5", Color(0xFF3F51B5)), // Royal Blue
        Pair("#42A5F5", Color(0xFF42A5F5)), // Sky Blue
        Pair("#29B6F6", Color(0xFF29B6F6)), // Cerulean
        Pair("#00BCD4", Color(0xFF00BCD4)), // Cyan
        Pair("#26C6DA", Color(0xFF26C6DA)), // Aqua
        Pair("#009688", Color(0xFF009688)), // Deep Teal
        Pair("#26A69A", Color(0xFF26A69A)), // Mint Teal

        // Greens & Limes
        Pair("#43A047", Color(0xFF43A047)), // Emerald
        Pair("#66BB6A", Color(0xFF66BB6A)), // Leaf Green
        Pair("#81C784", Color(0xFF81C784)), // Sage Green
        Pair("#9CCC65", Color(0xFF9CCC65)), // Lime
        Pair("#D4E157", Color(0xFFD4E157)), // Olive Lime

        // Earth, Slates & Monochromes
        Pair("#8D6E63", Color(0xFF8D6E63)), // Warm Brown
        Pair("#A1887F", Color(0xFFA1887F)), // Taupe
        Pair("#78909C", Color(0xFF78909C)), // Slate Blue
        Pair("#90A4AE", Color(0xFF90A4AE)), // Cool Grey
        Pair("#607D8B", Color(0xFF607D8B)), // Blue Grey
        Pair("#B0BEC5", Color(0xFFB0BEC5)), // Silver Grey
        Pair("#E0E0E0", Color(0xFFE0E0E0)), // White Smoke
        Pair("#424242", Color(0xFF424242))  // Charcoal
    )

    /**
     * Safely parse a hex color string (e.g. "#F08A6C") to Compose Color,
     * falling back to [DEFAULT_COLOR] on null or invalid format.
     */
    fun parseColor(hex: String?): Color {
        if (hex.isNullOrBlank()) return DEFAULT_COLOR
        return try {
            val cleanHex = hex.removePrefix("#")
            val colorLong = when (cleanHex.length) {
                6 -> 0xFF000000 or cleanHex.toLong(16)
                8 -> cleanHex.toLong(16)
                else -> return DEFAULT_COLOR
            }
            Color(colorLong)
        } catch (e: Exception) {
            DEFAULT_COLOR
        }
    }
}
