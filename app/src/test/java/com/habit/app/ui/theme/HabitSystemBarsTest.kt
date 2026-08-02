package com.habit.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitSystemBarsTest {
    @Test
    fun everyBuiltInLightThemeUsesItsBackgroundWithDarkSystemBarIcons() {
        HabitThemeId.entries.forEach { theme ->
            val appearance = systemBarAppearance(theme)

            assertTrue("$theme should use dark system-bar icons", appearance.darkIcons)
            assertEquals(theme.colorScheme().background, appearance.background)
        }
    }
}
