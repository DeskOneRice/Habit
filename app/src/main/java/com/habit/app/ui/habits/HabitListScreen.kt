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
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import com.habit.app.ui.components.habitEmoji

@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onCreate: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCategories: () -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var archivedExpanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag("habit_list_screen"),
        topBar = {
            HabitTopAppBar(
                title = "我的习惯",
                navigationMode = NavigationMode.MENU,
                onNavigation = onOpenDrawer,
            ) {
                TextButton(onClick = onCategories) {
                    Text("管理分类")
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("create_habit"),
                text = { Text("新建习惯") },
                icon = { Text("＋") },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("habit_list_scroll"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                end = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
        ) {
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
                        HabitRow(habit = habit) { onOpenDetail(habit.id) }
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
                        HabitRow(habit = habit) { onOpenDetail(habit.id) }
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
