package com.habit.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val NearWhiteBackground = Color(0xFFFAFCFD)
private val WhiteSurface = Color(0xFFFFFFFF)
private val SoftNeutral = Color(0xFFF3F7F9)
private val SoftNeutralHigh = Color(0xFFEAF0F2)
private val PrimaryText = Color(0xFF263840)
private val SecondaryText = Color(0xFF708087)
private val PrimaryForeground = Color(0xFF263238)
private val SecondaryAccent = Color(0xFF6F919E)
private val TertiaryAccent = Color(0xFF7F979E)
private val Outline = Color(0xFFBBC8CD)
private val OutlineVariant = Color(0xFFDCE5E8)

private fun nearWhiteScheme(
    primary: Color,
    primaryContainer: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = PrimaryForeground,
    primaryContainer = primaryContainer,
    onPrimaryContainer = PrimaryText,
    inversePrimary = primaryContainer,
    secondary = SecondaryAccent,
    onSecondary = Color.White,
    secondaryContainer = SoftNeutralHigh,
    onSecondaryContainer = PrimaryText,
    tertiary = TertiaryAccent,
    onTertiary = Color.White,
    tertiaryContainer = SoftNeutral,
    onTertiaryContainer = PrimaryText,
    background = NearWhiteBackground,
    onBackground = PrimaryText,
    surface = WhiteSurface,
    onSurface = PrimaryText,
    surfaceVariant = SoftNeutral,
    onSurfaceVariant = SecondaryText,
    surfaceTint = primary,
    inverseSurface = PrimaryText,
    inverseOnSurface = NearWhiteBackground,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Color(0x52000000),
    surfaceBright = WhiteSurface,
    surfaceDim = Color(0xFFE5ECEF),
    surfaceContainer = Color(0xFFF5F8F9),
    surfaceContainerHigh = Color(0xFFF0F5F6),
    surfaceContainerHighest = Color(0xFFEBF1F3),
    surfaceContainerLow = Color(0xFFF8FAFB),
    surfaceContainerLowest = WhiteSurface,
    primaryFixed = primaryContainer,
    primaryFixedDim = primary,
    onPrimaryFixed = PrimaryText,
    onPrimaryFixedVariant = PrimaryText,
    secondaryFixed = SoftNeutralHigh,
    secondaryFixedDim = SecondaryAccent,
    onSecondaryFixed = PrimaryText,
    onSecondaryFixedVariant = PrimaryText,
    tertiaryFixed = SoftNeutral,
    tertiaryFixedDim = TertiaryAccent,
    onTertiaryFixed = PrimaryText,
    onTertiaryFixedVariant = PrimaryText,
)

val SkyBlueScheme = nearWhiteScheme(
    primary = Color(0xFF8DB9CC),
    primaryContainer = Color(0xFFEAF4F8),
)

val SoftPinkScheme = nearWhiteScheme(
    primary = Color(0xFFDF988F),
    primaryContainer = Color(0xFFF9ECEA),
)

val SageGreenScheme = nearWhiteScheme(
    primary = Color(0xFFA8C39D),
    primaryContainer = Color(0xFFEDF5EA),
)

val MistPurpleScheme = nearWhiteScheme(
    primary = Color(0xFFAAA0C1),
    primaryContainer = Color(0xFFF0EDF5),
)

val NeutralGrayScheme = nearWhiteScheme(
    primary = Color(0xFF9CA5A7),
    primaryContainer = Color(0xFFF0F2F2),
)

fun HabitThemeId.colorScheme(): ColorScheme = when (this) {
    HabitThemeId.SKY_BLUE -> SkyBlueScheme
    HabitThemeId.SOFT_PINK -> SoftPinkScheme
    HabitThemeId.SAGE_GREEN -> SageGreenScheme
    HabitThemeId.MIST_PURPLE -> MistPurpleScheme
    HabitThemeId.NEUTRAL_GRAY -> NeutralGrayScheme
}
