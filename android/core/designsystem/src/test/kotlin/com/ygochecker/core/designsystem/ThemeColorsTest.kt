package com.ygochecker.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorsTest {
    @Test
    fun `dark scheme uses the cyan duel HUD primary`() {
        assertEquals(Color(0xFF00E5FF), DuelDarkColorScheme.primary)
    }

    @Test
    fun `dark scheme background is the HUD navy, not the old duel navy`() {
        assertEquals(Color(0xFF121620), DuelDarkColorScheme.background)
    }

    @Test
    fun `dark card surface container matches the accent-card mockup`() {
        assertEquals(Color(0xFF1A1F2C), DuelDarkColorScheme.surfaceContainer)
    }

    @Test
    fun `light scheme primary is a contrast-safe darker cyan, not the raw HUD cyan`() {
        assertEquals(Color(0xFF0089A3), DuelLightColorScheme.primary)
    }

    @Test
    fun `extended colors expose a restricted rarity gold token`() {
        assertEquals(Color(0xFFE8C45C), DuelDarkExtendedColors.rarityGold)
        assertEquals(Color(0xFF8B6F1F), DuelLightExtendedColors.rarityGold)
    }
}
