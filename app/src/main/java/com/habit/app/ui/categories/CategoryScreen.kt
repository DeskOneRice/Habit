package com.habit.app.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.Category

@Composable fun CategoryScreen(viewModel: CategoryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle(); var create by remember { mutableStateOf(false) }; var rename by remember { mutableStateOf<Category?>(null) }; var deleting by remember { mutableStateOf<Category?>(null) }; var target by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().testTag("category_screen").padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("分类管理", style = MaterialTheme.typography.headlineSmall); TextButton(onBack) { Text("完成") } }
        Button({ create = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("新建分类") }
        state.categories.forEach { category -> ListItem(headlineContent = { Text(category.name) }, supportingContent = { Text(if (category.isPreset) "预设分类" else "${state.habitCounts[category.id] ?: 0} 个习惯") }, trailingContent = { Row { if (category.isPreset) TextButton({ viewModel.setPresetHidden(category.id, !category.isHidden) }) { Text(if (category.isHidden) "恢复" else "隐藏") } else { TextButton({ rename = category }) { Text("重命名") }; TextButton({ deleting = category; target = null }) { Text("删除") } } } }) }
    }
    if (create) CategoryNameDialog("新建分类", "", { viewModel.create(it) { create = false } }) { create = false }
    rename?.let { category -> CategoryNameDialog("重命名分类", category.name, { viewModel.rename(category.id, it) { rename = null } }) { rename = null } }
    deleting?.let { source -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除分类") }, text = { Column { Text(if ((state.habitCounts[source.id] ?: 0) > 0) "请先选择迁移目标，分类内的习惯将被迁移。" else "请选择迁移目标后删除分类。", Modifier.testTag("category_migration_warning")) ; state.categories.filter { it.id != source.id && !it.isHidden }.forEach { category -> FilterChip(selected = target == category.id, onClick = { target = category.id }, label = { Text(category.name) }) } } }, confirmButton = { Button({ target?.let { viewModel.migrateAndDelete(source.id, it) { deleting = null } } }, Modifier.testTag("category_migration_confirm"), enabled = target != null) { Text("确认迁移并删除") } }, dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
}
@Composable private fun CategoryNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) { var value by remember { mutableStateOf(initial) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text("分类名称") }) }, confirmButton = { Button({ onConfirm(value) }, enabled = value.trim().isNotEmpty()) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } }) }
