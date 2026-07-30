package com.habit.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.unit.dp
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.colorScheme

private data class ThemeChoice(
    val id: HabitThemeId,
    val label: String,
)

private val themeChoices = listOf(
    ThemeChoice(HabitThemeId.SKY_BLUE, "天蓝"),
    ThemeChoice(HabitThemeId.SOFT_PINK, "柔粉"),
    ThemeChoice(HabitThemeId.SAGE_GREEN, "鼠尾草绿"),
    ThemeChoice(HabitThemeId.MIST_PURPLE, "雾紫"),
    ThemeChoice(HabitThemeId.NEUTRAL_GRAY, "中性灰"),
)

@Composable
fun ThemePicker(
    selectedTheme: HabitThemeId,
    onThemeSelected: (HabitThemeId) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        themeChoices.forEach { choice ->
            val selected = selectedTheme == choice.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onThemeSelected(choice.id) },
                    )
                    .testTag(
                        if (selected) {
                            "theme_${choice.id}_selected"
                        } else {
                            "theme_${choice.id}"
                        },
                    )
                    .semantics {
                        contentDescription = "${choice.label}主题${if (selected) "，已选择" else ""}"
                    }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(choice.id.colorScheme().primary, CircleShape),
                )
                Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                if (selected) {
                    Text("已选择", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
