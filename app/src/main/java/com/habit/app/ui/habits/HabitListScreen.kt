package com.habit.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.Habit
import com.habit.app.ui.components.habitEmoji

@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onCategories: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var archivedExpanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag("habit_list_screen"),
        bottomBar = {
            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 20.dp)
                    .testTag("create_habit"),
            ) {
                Text("新建习惯")
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("habit_list_scroll"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = contentPadding.calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("习惯", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onCategories) { Text("管理分类") }
                }
            }
            state.categories
                .filter { category -> state.active.any { it.categoryId == category.id } }
                .forEach { category ->
                    item(key = "category_${category.id}") {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    items(
                        items = state.active.filter { it.categoryId == category.id },
                        key = { habit -> habit.id },
                    ) { habit ->
                        HabitRow(habit = habit) { onEdit(habit.id) }
                    }
                }
            if (state.active.isEmpty()) {
                item {
                    Text(
                        "还没有进行中的习惯",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            if (state.archived.isNotEmpty()) {
                item {
                    TextButton(onClick = { archivedExpanded = !archivedExpanded }) {
                        Text("已归档 (${state.archived.size})")
                    }
                }
                if (archivedExpanded) {
                    items(
                        items = state.archived,
                        key = { habit -> "archived_${habit.id}" },
                    ) { habit ->
                        HabitRow(habit = habit) { onEdit(habit.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRow(habit: Habit, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(habit.name) },
    leadingContent = { Text(habitEmoji(habit.iconKey)) },
    trailingContent = {
        Box(
            Modifier
                .size(18.dp)
                .background(Color(habit.themeColor), CircleShape),
        )
    },
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
)
