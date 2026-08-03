package com.habit.app.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.Category
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode

@Composable
fun CategoryScreen(
    viewModel: CategoryViewModel,
    navigationMode: NavigationMode,
    onNavigation: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var target by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            HabitTopAppBar(
                title = "分类管理",
                navigationMode = navigationMode,
                onNavigation = onNavigation,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("category_screen")
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "整理你的习惯",
                style = MaterialTheme.typography.labelMedium,
            )
            Button(
                onClick = { create = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("新建分类")
            }
            state.categories.forEach { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = {
                        Text(
                            if (category.isPreset) {
                                "预设分类"
                            } else {
                                "${state.habitCounts[category.id] ?: 0} 个习惯"
                            },
                        )
                    },
                    trailingContent = {
                        Row {
                            if (category.isPreset) {
                                TextButton(
                                    onClick = {
                                        viewModel.setPresetHidden(category.id, !category.isHidden)
                                    },
                                ) {
                                    Text(if (category.isHidden) "恢复" else "隐藏")
                                }
                            } else {
                                TextButton(onClick = { rename = category }) {
                                    Text("重命名")
                                }
                                TextButton(
                                    onClick = {
                                        deleting = category
                                        target = null
                                    },
                                    modifier = Modifier.testTag("category_delete_${category.id}"),
                                ) {
                                    Text("删除")
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("category_row_${category.id}"),
                )
            }
        }
    }

    if (create) {
        CategoryNameDialog(
            title = "新建分类",
            initial = "",
            saving = state.saving,
            onConfirm = { viewModel.create(it) { create = false } },
            onDismiss = { create = false },
        )
    }
    rename?.let { category ->
        CategoryNameDialog(
            title = "重命名分类",
            initial = category.name,
            saving = state.saving,
            onConfirm = { viewModel.rename(category.id, it) { rename = null } },
            onDismiss = { rename = null },
        )
    }
    deleting?.let { source ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分类") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = if ((state.habitCounts[source.id] ?: 0) > 0) {
                            "请先选择迁移目标，分类内的习惯将被迁移。"
                        } else {
                            "请选择迁移目标后删除分类。"
                        },
                        modifier = Modifier.testTag("category_migration_warning"),
                    )
                    state.categories
                        .filter { it.id != source.id && !it.isHidden }
                        .forEach { category ->
                            FilterChip(
                                selected = target == category.id,
                                onClick = { target = category.id },
                                label = { Text(category.name) },
                                modifier = Modifier.testTag("category_migration_target_${category.id}"),
                            )
                        }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        target?.let {
                            viewModel.migrateAndDelete(source.id, it) { deleting = null }
                        }
                    },
                    modifier = Modifier.testTag("category_migration_confirm"),
                    enabled = target != null,
                ) {
                    Text("确认迁移并删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String,
    saving: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                enabled = !saving,
                label = { Text("分类名称") },
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                modifier = Modifier.testTag("category_save"),
                enabled = value.trim().isNotEmpty() && !saving,
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("取消")
            }
        },
    )
}
