package com.habit1.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitThemeCustomizationTest {

    @Test
    fun habitColors_paletteContainsCuratedColors() {
        assertTrue("Palette must contain curated colors", HabitColors.PALETTE.size >= 16)
        // Verify default color is peach
        assertEquals("#F08A6C", HabitColors.DEFAULT_COLOR_HEX)
        assertEquals(HabitColors.PALETTE.first().first, HabitColors.DEFAULT_COLOR_HEX)
    }

    @Test
    fun habitColors_parseColor_handlesValidHexAndFallbacks() {
        // Valid 6-digit hex
        val color1 = HabitColors.parseColor("#42A5F5")
        assertEquals(Color(0xFF42A5F5), color1)

        // Valid without hash
        val color2 = HabitColors.parseColor("42A5F5")
        assertEquals(Color(0xFF42A5F5), color2)

        // Valid 8-digit ARGB hex
        val color3 = HabitColors.parseColor("#FF42A5F5")
        assertEquals(Color(0xFF42A5F5), color3)

        // Null and blank fallback to DEFAULT_COLOR
        assertEquals(HabitColors.DEFAULT_COLOR, HabitColors.parseColor(null))
        assertEquals(HabitColors.DEFAULT_COLOR, HabitColors.parseColor(""))
        assertEquals(HabitColors.DEFAULT_COLOR, HabitColors.parseColor("   "))

        // Invalid hex format fallback to DEFAULT_COLOR
        assertEquals(HabitColors.DEFAULT_COLOR, HabitColors.parseColor("INVALID"))
        assertEquals(HabitColors.DEFAULT_COLOR, HabitColors.parseColor("#123"))
    }

    @Test
    fun habitIcons_libraryContainsExact36CuratedIcons() {
        assertEquals("Library must contain exactly 36 curated vector icons (Decision 6.3)", 36, HabitIcons.ICONS.size)

        // Verify key resolution
        assertNotNull(HabitIcons.getVectorIcon("fitness"))
        assertNotNull(HabitIcons.getVectorIcon("health"))
        assertNotNull(HabitIcons.getVectorIcon("book"))
        assertNotNull(HabitIcons.getVectorIcon("work"))
        assertNotNull(HabitIcons.getVectorIcon("home"))

        // Unknown key returns null
        assertNull(HabitIcons.getVectorIcon("unknown_key_xyz"))
        assertNull(HabitIcons.getVectorIcon(null))
    }

    @Test
    fun habitIcons_isEmoji_detectsUnicodeEmojisVsIconKeys() {
        // Standard icon keys are not emoji
        assertFalse(HabitIcons.isEmoji("fitness"))
        assertFalse(HabitIcons.isEmoji("health"))
        assertFalse(HabitIcons.isEmoji(null))
        assertFalse(HabitIcons.isEmoji(""))

        // Real Unicode emojis
        assertTrue(HabitIcons.isEmoji("🏃"))
        assertTrue(HabitIcons.isEmoji("🧘"))
        assertTrue(HabitIcons.isEmoji("📚"))
        assertTrue(HabitIcons.isEmoji("💡"))
    }
}
