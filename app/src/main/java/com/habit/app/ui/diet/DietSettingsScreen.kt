package com.habit.app.ui.diet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode

@Composable
fun DietSettingsScreen(viewModel: DietSettingsViewModel, onOpenDrawer: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var enabled by remember(state.preferences.dailyGoalEnabled) { mutableStateOf(state.preferences.dailyGoalEnabled) }
    Scaffold(topBar = { HabitTopAppBar("饮食设置", NavigationMode.MENU, onOpenDrawer) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HabitCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("每日热量目标", style = MaterialTheme.typography.titleMedium)
                        Text("可选，仅用于中性进度显示", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(enabled, { enabled = it })
                }
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(state.goalText, viewModel::updateGoalText, label = { Text("目标热量（kcal）") }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                Button({ viewModel.saveGoal(enabled) }, modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}
