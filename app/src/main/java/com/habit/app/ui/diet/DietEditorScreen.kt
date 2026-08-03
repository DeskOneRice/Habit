package com.habit.app.ui.diet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealType
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietEditorScreen(
    viewModel: DietEditorViewModel,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            HabitTopAppBar(if (isEditing) "编辑饮食" else "记一餐", NavigationMode.BACK, onBack) {
                if (isEditing) TextButton(onClick = { showDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp).testTag("diet_editor"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isEditing) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (state.recordType == DietRecordType.MEAL) "正餐 / 加餐" else "饮品") },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.recordType == DietRecordType.MEAL, { viewModel.update { copy(recordType = DietRecordType.MEAL) } }, { Text("正餐 / 加餐") })
                    FilterChip(state.recordType == DietRecordType.BEVERAGE, { viewModel.update { copy(recordType = DietRecordType.BEVERAGE) } }, { Text("饮品") })
                }
            }
            Text("发生时间", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f).testTag("diet_choose_date"),
                ) { Text(state.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))) }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f).testTag("diet_choose_time"),
                ) { Text(state.time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            }
            if (state.recordType == DietRecordType.MEAL) MealFields(state, viewModel) else BeverageFields(state, viewModel)
            OutlinedTextField(
                value = state.finalCaloriesText,
                onValueChange = { value -> viewModel.update { copy(finalCaloriesText = value.filter(Char::isDigit)) } },
                label = { Text("整餐总热量（可选，kcal）") },
                modifier = Modifier.fillMaxWidth().testTag("diet_final_calories"),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = { value -> viewModel.update { copy(note = value) } },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
            )
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("diet_save"),
            ) { Text(if (state.isSaving) "正在保存…" else "保存") }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("删除这条记录？") },
        text = { Text("关联的食物和饮品属性也会一起删除。") },
        confirmButton = { TextButton(onClick = { viewModel.delete(onBack) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
    )
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.update { withDate(selected) }
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = pickerState) }
    }
    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.time.hour,
            initialMinute = state.time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { withTime(LocalTime.of(pickerState.hour, pickerState.minute)) }
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun MealFields(state: DietEditorUiState, viewModel: DietEditorViewModel) {
    Text("餐次", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MealType.entries.forEach { type ->
            FilterChip(state.mealType == type, { viewModel.update { copy(mealType = type) } }, { Text(type.cn()) })
        }
    }
    OutlinedTextField(
        state.description,
        { value -> viewModel.update { copy(description = value) } },
        label = { Text("这一餐吃了什么") },
        modifier = Modifier.fillMaxWidth().testTag("diet_description"),
        minLines = 2,
    )
    Text("食物明细（可选）", style = MaterialTheme.typography.titleMedium)
    state.foodItems.forEachIndexed { index, item ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(item.name, { viewModel.updateFood(index, it, item.portionText.orEmpty(), item.calories?.toString().orEmpty()) }, label = { Text("食物") }, modifier = Modifier.weight(1.2f).testTag("diet_food_name_$index"))
            OutlinedTextField(item.portionText.orEmpty(), { viewModel.updateFood(index, item.name, it, item.calories?.toString().orEmpty()) }, label = { Text("份量") }, modifier = Modifier.weight(0.8f))
            OutlinedTextField(item.calories?.toString().orEmpty(), { viewModel.updateFood(index, item.name, item.portionText.orEmpty(), it.filter(Char::isDigit)) }, label = { Text("kcal") }, modifier = Modifier.weight(0.7f).testTag("diet_food_calories_$index"))
        }
        TextButton(onClick = { viewModel.removeFood(index) }) { Text("移除此项") }
    }
    OutlinedButton(onClick = viewModel::addFoodItem, modifier = Modifier.testTag("diet_food_add")) { Text("＋ 添加食物") }
}

@Composable
private fun BeverageFields(state: DietEditorUiState, viewModel: DietEditorViewModel) {
    Text("饮品分类", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        BeverageCategory.entries.take(4).forEach { category ->
            FilterChip(state.beverageCategory == category, { viewModel.update { copy(beverageCategory = category) } }, { Text(category.cn()) })
        }
    }
    DietField(state.beverageName, "饮品名称 *", "diet_beverage_name") { viewModel.update { copy(beverageName = it) } }
    DietField(state.brandOrStore, "品牌 / 门店") { viewModel.update { copy(brandOrStore = it) } }
    DietField(state.sizeOrVolume, "杯型 / 容量") { viewModel.update { copy(sizeOrVolume = it) } }
    DietField(state.temperature, "冷热") { viewModel.update { copy(temperature = it) } }
    DietField(state.iceLevel, "冰量") { viewModel.update { copy(iceLevel = it) } }
    DietField(state.sweetness, "甜度") { viewModel.update { copy(sweetness = it) } }
    DietField(state.toppings, "加料（用顿号分隔）") { viewModel.update { copy(toppings = it) } }
    DietField(state.cupCountText, "杯数") { viewModel.update { copy(cupCountText = it.filter(Char::isDigit)) } }
}

@Composable
private fun DietField(value: String, label: String, tag: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().then(if (tag == null) Modifier else Modifier.testTag(tag)))
}

private fun MealType.cn() = when (this) { MealType.BREAKFAST -> "早餐"; MealType.LUNCH -> "午餐"; MealType.DINNER -> "晚餐"; MealType.SNACK -> "加餐" }
private fun BeverageCategory.cn() = when (this) { BeverageCategory.COFFEE -> "咖啡"; BeverageCategory.MILK_TEA -> "奶茶"; BeverageCategory.TEA -> "茶"; BeverageCategory.FRUIT_DRINK -> "果饮"; BeverageCategory.DAIRY -> "乳饮"; BeverageCategory.OTHER -> "其他" }
