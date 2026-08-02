package com.habit.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.habit.app.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun HabitDrawerContent(
    selectedRoute: String?,
    progress: Float,
    habitExpanded: Boolean,
    dietExpanded: Boolean,
    onToggleHabit: () -> Unit,
    onToggleDiet: () -> Unit,
    onDestination: (HabitDestination) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.habit_logo),
                contentDescription = "Habit",
                modifier = Modifier.size(46.dp),
            )
            Column {
                Text("Habit", style = MaterialTheme.typography.titleLarge)
                Text("积累看得见的日常", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(22.dp))
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text("今日进度 ${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        DrawerItem(DrawerDestination(HabitDestination.Workbench, "今日工作台", "⌂"), selectedRoute, onDestination)
        DrawerGroupHeader("习惯", "✓", habitExpanded, onToggleHabit)
        if (habitExpanded) habitDestinations.forEach { item ->
            DrawerItem(item, selectedRoute, onDestination, Modifier.padding(start = 12.dp))
        }
        DrawerGroupHeader("饮食", "☕", dietExpanded, onToggleDiet)
        if (dietExpanded) dietDestinations.forEach { item ->
            DrawerItem(item, selectedRoute, onDestination, Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(4.dp))
        DrawerItem(DrawerDestination(HabitDestination.Settings, "主题与设置", "⚙"), selectedRoute, onDestination)
    }
}

@Composable
private fun DrawerGroupHeader(label: String, symbol: String, expanded: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(symbol)
        Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(if (expanded) "⌃" else "⌄")
    }
}

@Composable
private fun DrawerItem(
    item: DrawerDestination,
    selectedRoute: String?,
    onDestination: (HabitDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
            NavigationDrawerItem(
                label = { Text(item.label) },
                icon = { Text(item.symbol) },
                selected = selectedRoute == item.destination.route,
                onClick = { onDestination(item.destination) },
        modifier = modifier.testTag("drawer_${item.destination.route}"),
            )
}
