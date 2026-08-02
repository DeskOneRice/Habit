package com.habit.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class HabitSystemBarAppearance(
    val background: Color,
    val darkIcons: Boolean,
)

fun systemBarAppearance(themeId: HabitThemeId): HabitSystemBarAppearance =
    HabitSystemBarAppearance(
        background = themeId.colorScheme().background,
        darkIcons = true,
    )

@Composable
fun HabitTheme(
    themeId: HabitThemeId,
    content: @Composable () -> Unit,
) {
    val colorScheme = themeId.colorScheme()
    val systemBars = systemBarAppearance(themeId)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = systemBars.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = systemBars.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = systemBars.darkIcons
                    isAppearanceLightNavigationBars = systemBars.darkIcons
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
