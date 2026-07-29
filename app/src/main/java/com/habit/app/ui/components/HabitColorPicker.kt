package com.habit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private data class HabitColorOption(
    val key: String,
    val label: String,
    val value: Long,
)

private val colors = listOf(
    HabitColorOption("sky", "天蓝", 0xFF8DB9CC),
    HabitColorOption("rose", "柔粉", 0xFFDF988F),
    HabitColorOption("sage", "鼠尾草绿", 0xFFA8C39D),
    HabitColorOption("lavender", "雾紫", 0xFFAAA0C1),
    HabitColorOption("gray", "中性灰", 0xFF9CA5A7),
)

@Composable
fun HabitColorPicker(selected: Long, onSelected: (Long) -> Unit) = FlowRow {
    colors.forEach { option ->
        val isSelected = selected == option.value
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(Color(option.value), CircleShape)
                .selectedBorder(isSelected)
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = { onSelected(option.value) },
                )
                .semantics { contentDescription = "习惯颜色：${option.label}" }
                .testTag("habit_color_${option.key}"),
        ) {
            if (isSelected) {
                Text("✓", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun Modifier.selectedBorder(selected: Boolean, width: Dp = 3.dp): Modifier =
    if (selected) border(width, MaterialTheme.colorScheme.onSurface, CircleShape) else this
