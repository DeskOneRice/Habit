package com.habit.app.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.HabitTopAction
import com.habit.app.ui.components.NavigationMode

@Composable
fun CategoryScreen(
    viewModel: CategoryViewModel,
    navigationMode: NavigationMode,
    onNavigation: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var create by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ManagedCategoryItem?>(null) }
    var deleting by remember { mutableStateOf<ManagedCategoryItem?>(null) }
    var target by remember { mutableStateOf<Long?>(null) }

    fun selectSection(section: CategorySection) {
        editing = null
        deleting = null
        target = null
        viewModel.selectSection(section)
    }

    Scaffold(
        topBar = {
            HabitTopAppBar(
                title = "分类管理",
                navigationMode = navigationMode,
                onNavigation = onNavigation,
            ) {
                HabitTopAction(
                    text = "＋",
                    contentDescription = "新建分类",
                    onClick = { create = true },
                    modifier = Modifier.testTag("category_add"),
                    textStyle = MaterialTheme.typography.titleLarge,
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("category_screen")
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategorySection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = { selectSection(section) },
                        label = { Text(section.label()) },
                        modifier = Modifier.weight(1f).testTag("category_section_${section.name.lowercase()}"),
                    )
                }
            }
            OutlinedButton(
                onClick = { create = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("新建分类") }

            CategoryGroup(
                title = "正在使用",
                items = state.items.filterNot(ManagedCategoryItem::isHidden),
                onEdit = { editing = it },
                onDelete = {
                    deleting = it
                    target = null
                },
            )
            CategoryGroup(
                title = "已隐藏",
                items = state.items.filter(ManagedCategoryItem::isHidden),
                onEdit = { editing = it },
                onDelete = {
                    deleting = it
                    target = null
                },
            )
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("category_message"))
            }
        }
    }

    if (create) {
        CategoryNameDialog(
            title = "新建${state.section.label()}分类",
            initial = "",
            saving = state.saving,
            message = state.message,
            onConfirm = { viewModel.create(it) { create = false } },
            onDismiss = { create = false; viewModel.clearMessage() },
        )
    }
    editing?.let { item ->
        CategoryEditDialog(
            item = item,
            saving = state.saving,
            message = state.message,
            onRename = { name -> viewModel.rename(item.section, item.id, name) { editing = null } },
            onToggleHidden = {
                viewModel.setHidden(item.id, !item.isHidden)
                editing = null
            },
            onDelete = {
                editing = null
                deleting = item
                target = null
            },
            onDismiss = { editing = null; viewModel.clearMessage() },
        )
    }
    deleting?.let { source ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) deleting = null },
            title = { Text("删除${source.name}") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (source.usageCount > 0) "请选择迁移目标，已有 ${source.usageCount} 条内容会迁移。"
                        else "请选择同组目标后删除分类。",
                        modifier = Modifier.testTag("category_migration_warning"),
                    )
                    state.items
                        .filter { it.id != source.id && !it.isHidden && it.section == source.section }
                        .forEach { item ->
                            FilterChip(
                                selected = target == item.id,
                                onClick = { target = item.id },
                                label = { Text(item.name) },
                                modifier = Modifier.testTag("category_migration_target_${item.id}"),
                            )
                        }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    onClick = { target?.let { viewModel.migrateAndDelete(source.id, it) { deleting = null } } },
                    modifier = Modifier.testTag("category_migration_confirm"),
                    enabled = target != null && !state.saving,
                ) { Text(if (state.saving) "处理中…" else "确认迁移并删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !state.saving) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryGroup(
    title: String,
    items: List<ManagedCategoryItem>,
    onEdit: (ManagedCategoryItem) -> Unit,
    onDelete: (ManagedCategoryItem) -> Unit,
) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium)
    items.forEach { item ->
        ListItem(
            headlineContent = { Text(item.name) },
            supportingContent = {
                Text(listOfNotNull(if (item.isPreset) "预设" else null, "使用 ${item.usageCount} 次").joinToString(" · "))
            },
            trailingContent = {
                Row {
                    TextButton(onClick = { onEdit(item) }) { Text("编辑") }
                    if (!item.isPreset) {
                        TextButton(
                            onClick = { onDelete(item) },
                            modifier = Modifier.testTag("category_delete_${item.id}"),
                        ) { Text("删除") }
                    }
                }
            },
            modifier = Modifier.testTag("category_row_${item.id}"),
        )
    }
}

@Composable
private fun CategoryEditDialog(
    item: ManagedCategoryItem,
    saving: Boolean,
    message: String?,
    onRename: (String) -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(item.id, item.name) { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("编辑分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value, { value = it }, label = { Text("分类名称") }, enabled = !saving)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onToggleHidden, enabled = !saving) { Text(if (item.isHidden) "恢复" else "隐藏") }
                    TextButton(onClick = onDelete, enabled = !saving) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onRename(value) }, enabled = value.isNotBlank() && !saving) {
                Text(if (saving) "保存中…" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String,
    saving: Boolean,
    message: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it }, enabled = !saving, label = { Text("分类名称") })
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                modifier = Modifier.testTag("category_save"),
                enabled = value.trim().isNotEmpty() && !saving,
            ) { Text(if (saving) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}

private fun CategorySection.label(): String = when (this) {
    CategorySection.HABIT -> "习惯"
    CategorySection.MEAL -> "餐食"
    CategorySection.BEVERAGE -> "饮品"
}
