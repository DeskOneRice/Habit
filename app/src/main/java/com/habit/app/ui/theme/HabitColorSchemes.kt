package com.habit.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFFFCFEFE)
private val PrimaryText = Color(0xFF30434D)
private val PrimaryForeground = Color(0xFF263238)

val SkyBlueScheme = lightColorScheme(
    primary = Color(0xFF8DB9CC),
    onPrimary = PrimaryForeground,
    primaryContainer = Color(0xFFDDEEF5),
    onPrimaryContainer = PrimaryText,
    background = Background,
    onBackground = PrimaryText,
    surface = Background,
    onSurface = PrimaryText,
)

val SoftPinkScheme = lightColorScheme(
    primary = Color(0xFFDF988F),
    onPrimary = PrimaryForeground,
    primaryContainer = Color(0xFFF7E5E2),
    onPrimaryContainer = PrimaryText,
    background = Background,
    onBackground = PrimaryText,
    surface = Background,
    onSurface = PrimaryText,
)

val SageGreenScheme = lightColorScheme(
    primary = Color(0xFFA8C39D),
    onPrimary = PrimaryForeground,
    primaryContainer = Color(0xFFE6F0E2),
    onPrimaryContainer = PrimaryText,
    background = Background,
    onBackground = PrimaryText,
    surface = Background,
    onSurface = PrimaryText,
)

val MistPurpleScheme = lightColorScheme(
    primary = Color(0xFFAAA0C1),
    onPrimary = PrimaryForeground,
    primaryContainer = Color(0xFFE9E6F0),
    onPrimaryContainer = PrimaryText,
    background = Background,
    onBackground = PrimaryText,
    surface = Background,
    onSurface = PrimaryText,
)

val NeutralGrayScheme = lightColorScheme(
    primary = Color(0xFF9CA5A7),
    onPrimary = PrimaryForeground,
    primaryContainer = Color(0xFFE8EAEB),
    onPrimaryContainer = PrimaryText,
    background = Background,
    onBackground = PrimaryText,
    surface = Background,
    onSurface = PrimaryText,
)

fun HabitThemeId.colorScheme(): ColorScheme = when (this) {
    HabitThemeId.SKY_BLUE -> SkyBlueScheme
    HabitThemeId.SOFT_PINK -> SoftPinkScheme
    HabitThemeId.SAGE_GREEN -> SageGreenScheme
    HabitThemeId.MIST_PURPLE -> MistPurpleScheme
    HabitThemeId.NEUTRAL_GRAY -> NeutralGrayScheme
}
