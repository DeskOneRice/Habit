package com.habit.app.ui.habits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.ui.components.EmojiPicker
import com.habit.app.ui.components.HabitColorPicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest

private val startDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun HabitEditorScreen(viewModel: HabitEditorViewModel, categories: CategoryRepository, onSaved: () -> Unit, onBack: () -> Unit, onArchive: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val errorRequester = remember { BringIntoViewRequester() }
    var groups by remember { mutableStateOf(emptyList<com.habit.app.domain.model.Category>()) }
    LaunchedEffect(categories) { categories.observeVisible().collectLatest { groups = it } }
    LaunchedEffect(state.nameError) { if (state.nameError != null) errorRequester.bringIntoView() }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }; var deleteStage by remember { mutableStateOf(0) }
    Scaffold(bottomBar = { Button({ viewModel.save(onSaved) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 20.dp).testTag("save_habit"), enabled = !state.saving) { Text(if (state.saving) "保存中…" else "保存") } }) { contentPadding ->
    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(scrollState).padding(20.dp).testTag("habit_editor_screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (state.habitId == null) "新建习惯" else "编辑习惯", style = MaterialTheme.typography.headlineSmall)
        state.nameError?.let { Text(it, modifier = Modifier.bringIntoViewRequester(errorRequester).testTag("habit_name_error"), color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(state.name, viewModel::onNameChange, Modifier.fillMaxWidth().testTag("habit_name"), label = { Text("习惯名称") }, singleLine = true)
        Text("选择图标"); EmojiPicker(state.iconKey, viewModel::onEmojiChange)
        Text("识别颜色"); HabitColorPicker(state.themeColor, viewModel::onColorChange)
        Text("分类")
        groups.forEach { category -> FilterChip(selected = state.categoryId == category.id, onClick = { viewModel.onCategoryChange(category.id) }, label = { Text(category.name) }) }
        Text("开始日期：${LocalDate.ofEpochDay(state.startEpochDay).format(startDateFormatter)}")
        OutlinedButton(
            onClick = { showStartDatePicker = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start_date_picker"),
        ) {
            Text("选择开始日期")
        }
        Text("每天重复", style = MaterialTheme.typography.titleMedium); Text("从开始日期起，每天都可以完成一次。", style = MaterialTheme.typography.bodyMedium)
        if (state.habitId != null) {
            OutlinedButton({ showArchive = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("archive_habit")) { Text("归档习惯") }
            TextButton({ deleteStage = 1 }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_habit")) { Text("删除习惯") }
        }
    }
    }
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate
                .ofEpochDay(state.startEpochDay)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedMillis ->
                            viewModel.onStartDateChange(
                                Instant
                                    .ofEpochMilli(selectedMillis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toEpochDay(),
                            )
                        }
                        showStartDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("取消")
                }
            },
            modifier = Modifier.testTag("start_date_dialog"),
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (showArchive) AlertDialog(onDismissRequest = { showArchive = false }, title = { Text("归档习惯？") }, text = { Text("归档后不会出现在当前习惯中。") }, confirmButton = { Button({ state.habitId?.let(onArchive); showArchive = false }, Modifier.testTag("archive_confirm")) { Text("确认归档") } }, dismissButton = { TextButton({ showArchive = false }) { Text("取消") } })
    if (deleteStage == 1) AlertDialog(onDismissRequest = { deleteStage = 0 }, title = { Text("删除习惯？") }, text = { Text("删除后，所有历史打卡记录都会被删除。", Modifier.testTag("delete_history_warning")) }, confirmButton = { Button({ deleteStage = 2 }, Modifier.testTag("delete_continue")) { Text("继续") } }, dismissButton = { TextButton({ deleteStage = 0 }) { Text("取消") } })
    if (deleteStage == 2) AlertDialog(onDismissRequest = { deleteStage = 0 }, title = { Text("确认永久删除？") }, text = { Text("此操作无法撤销。") }, confirmButton = { Button({ state.habitId?.let(onDelete); deleteStage = 0 }, Modifier.testTag("delete_confirm")) { Text("永久删除") } }, dismissButton = { TextButton({ deleteStage = 0 }) { Text("取消") } })
}
