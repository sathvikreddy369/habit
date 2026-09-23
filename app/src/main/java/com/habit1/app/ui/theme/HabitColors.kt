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
     * 16 curated, accessible, and vibrant palette colors for habit customization.
     */
    val PALETTE: List<Pair<String, Color>> = listOf(
        Pair("#F08A6C", Color(0xFFF08A6C)), // Peach / Coral (Default)
        Pair("#E57373", Color(0xFFE57373)), // Soft Red
        Pair("#EC407A", Color(0xFFEC407A)), // Rose Pink
        Pair("#AB47BC", Color(0xFFAB47BC)), // Purple
        Pair("#7E57C2", Color(0xFF7E57C2)), // Deep Purple
        Pair("#5C6BC0", Color(0xFF5C6BC0)), // Indigo
        Pair("#42A5F5", Color(0xFF42A5F5)), // Blue
        Pair("#29B6F6", Color(0xFF29B6F6)), // Light Blue
        Pair("#26C6DA", Color(0xFF26C6DA)), // Cyan
        Pair("#26A69A", Color(0xFF26A69A)), // Teal
        Pair("#66BB6A", Color(0xFF66BB6A)), // Emerald Green
        Pair("#9CCC65", Color(0xFF9CCC65)), // Lime
        Pair("#FFA726", Color(0xFFFFA726)), // Amber
        Pair("#FF7043", Color(0xFFFF7043)), // Deep Orange
        Pair("#78909C", Color(0xFF78909C)), // Slate Blue
        Pair("#8D6E63", Color(0xFF8D6E63))  // Warm Brown
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
