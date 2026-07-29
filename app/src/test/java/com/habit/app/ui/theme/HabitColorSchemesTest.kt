package com.habit.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitColorSchemesTest {
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
