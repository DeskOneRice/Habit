package com.habit.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
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
    onDestination: (HabitDestination) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🌱", style = MaterialTheme.typography.headlineMedium)
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
        topLevelDestinations.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.label) },
                icon = { Text(item.symbol) },
                selected = selectedRoute == item.destination.route,
                onClick = { onDestination(item.destination) },
                modifier = Modifier.testTag("drawer_${item.destination.route}"),
            )
        }
    }
}
