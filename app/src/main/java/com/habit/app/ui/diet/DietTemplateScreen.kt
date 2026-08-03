package com.habit.app.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.DietTemplate
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode

@Composable
fun DietTemplateScreen(
    viewModel: DietTemplateViewModel,
    onOpenDrawer: () -> Unit,
    onUseTemplate: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<DietTemplate?>(null) }
    var renameTarget by remember { mutableStateOf<DietTemplate?>(null) }
    var renameText by remember { mutableStateOf("") }
    Scaffold(topBar = { HabitTopAppBar("饮食模板", NavigationMode.MENU, onOpenDrawer) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TemplateGroupHeader("🍱 正餐 / 加餐", state.mealExpanded, viewModel::toggleMeal) }
            if (state.mealExpanded) state.mealTemplates.forEachIndexed { index, template ->
                item(key = "meal_${template.id}") {
                    TemplateCard(template, index > 0, index < state.mealTemplates.lastIndex, onUseTemplate,
                        { viewModel.move(template.id, -1) }, { viewModel.move(template.id, 1) },
                        { renameTarget = template; renameText = template.name }, { deleteTarget = template })
                }
            }
            item { TemplateGroupHeader("☕ 饮品", state.beverageExpanded, viewModel::toggleBeverage) }
            if (state.beverageExpanded) state.beverageTemplates.forEachIndexed { index, template ->
                item(key = "drink_${template.id}") {
                    TemplateCard(template, index > 0, index < state.beverageTemplates.lastIndex, onUseTemplate,
                        { viewModel.move(template.id, -1) }, { viewModel.move(template.id, 1) },
                        { renameTarget = template; renameText = template.name }, { deleteTarget = template })
                }
            }
            if (state.templates.isEmpty()) item {
                Text("还没有模板。记录饮食时点击“存为模板”即可创建。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
    deleteTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除“${template.name}”？") },
            text = { Text("只删除模板，不影响已经保存的饮食记录。") },
            confirmButton = { TextButton(onClick = { viewModel.delete(template.id); deleteTarget = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    renameTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名模板") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("模板名称") }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.rename(template.id, renameText); renameTarget = null },
                    enabled = renameText.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TemplateGroupHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(if (expanded) "收起" else "展开")
    }
}

@Composable
private fun TemplateCard(
    template: DietTemplate,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUse: (Long) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    HabitCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        template.draft.description.ifBlank { null },
                        template.draft.manualFinalCalories?.let { "$it kcal" },
                        template.photos.takeIf { it.isNotEmpty() }?.let { "${it.size} 张照片" },
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onUse(template.id) }) { Text("记录") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("上移") }
            TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("下移") }
            TextButton(onClick = onRename) { Text("重命名") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}
