package com.habit.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitColorSchemesTest {
    @Test
    fun skyBlueUsesNearWhiteSurfacesAndNoMaterialPurpleFallbacks() {
        val scheme = SkyBlueScheme

        assertEquals(Color(0xFFFAFCFD), scheme.background)
        assertEquals(Color.White, scheme.surface)
        assertEquals(Color(0xFFEAF4F8), scheme.primaryContainer)
        val forbidden = setOf(
            Color(0xFF625B71),
            Color(0xFF7D5260),
            Color(0xFFE8DEF8),
        )
        assertTrue(
            listOf(
                scheme.secondary,
                scheme.tertiary,
                scheme.secondaryContainer,
                scheme.tertiaryContainer,
            ).none { it in forbidden },
        )
    }

    @Test
    fun allBuiltInPrimaryForegroundPairsMeetNormalTextContrast() {
        val schemes = listOf(
            "SKY_BLUE" to SkyBlueScheme,
            "SOFT_PINK" to SoftPinkScheme,
            "SAGE_GREEN" to SageGreenScheme,
            "MIST_PURPLE" to MistPurpleScheme,
            "NEUTRAL_GRAY" to NeutralGrayScheme,
        )

        schemes.forEach { (name, scheme) ->
            val ratio = contrastRatio(scheme)
            assertTrue("$name contrast ratio was $ratio", ratio >= 4.5f)
        }
    }

    private fun contrastRatio(scheme: ColorScheme): Float {
        val primaryLuminance = scheme.primary.luminance()
        val foregroundLuminance = scheme.onPrimary.luminance()
        return (maxOf(primaryLuminance, foregroundLuminance) + 0.05f) /
            (minOf(primaryLuminance, foregroundLuminance) + 0.05f)
    }
}
