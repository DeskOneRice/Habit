package com.habit.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HabitEmoji(
    iconKey: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = habitEmoji(iconKey),
        modifier = modifier,
    )
}
