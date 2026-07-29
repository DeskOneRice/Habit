package com.habit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun HabitTheme(
    themeId: HabitThemeId,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = themeId.colorScheme(),
        content = content,
    )
}
