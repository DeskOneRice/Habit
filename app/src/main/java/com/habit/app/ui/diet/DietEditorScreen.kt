package com.habit.app.ui.diet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.habit.app.data.photos.CameraPhotoTarget
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.mealTypeDisplayOrder
import com.habit.app.domain.model.displayName
import com.habit.app.ui.components.CategoryChipFlow
import com.habit.app.ui.components.CategoryChipItem
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.HabitTopAction
import com.habit.app.ui.components.NavigationMode
import com.habit.app.ui.components.HabitDatePickerDialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietEditorScreen(
    viewModel: DietEditorViewModel,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onManageCategories: (DietRecordType) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showPhotoSource by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var templateWithPhotos by remember { mutableStateOf(false) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var cameraTarget by remember { mutableStateOf<CameraPhotoTarget?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        viewModel.importPhotos(it)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        cameraTarget?.let { viewModel.acceptCameraTarget(it, success) }
        cameraTarget = null
    }
    BackHandler { viewModel.cancel(onBack) }
    Scaffold(
        topBar = {
            HabitTopAppBar(if (isEditing) "编辑饮食" else "记一餐", NavigationMode.BACK, { viewModel.cancel(onBack) }) {
                if (isEditing) {
                    HabitTopAction(
                        text = if (state.isSaving) "保存中…" else "保存",
                        contentDescription = "保存饮食记录",
                        onClick = { viewModel.save(onSaved) },
                        modifier = Modifier.testTag("diet_save_top"),
                        enabled = !state.isSaving,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(18.dp).testTag("diet_editor"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isEditing) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(state.recordType.displayName()) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.recordType == DietRecordType.MEAL, { viewModel.update { copy(recordType = DietRecordType.MEAL) } }, { Text("餐食") })
                    FilterChip(state.recordType == DietRecordType.BEVERAGE, { viewModel.update { copy(recordType = DietRecordType.BEVERAGE) } }, { Text("饮品") })
                }
            }
            Text("${state.recordType.displayName()}分类", style = MaterialTheme.typography.titleMedium)
            CategoryChipFlow(
                items = state.dietCategories.map { CategoryChipItem(it.id, it.name) },
                selectedId = state.dietCategoryId,
                onSelected = viewModel::selectDietCategory,
                onCreate = { showCreateCategory = true },
                onManage = { onManageCategories(state.recordType) },
            )
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
            Text("照片（最多 3 张）", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.photos.forEachIndexed { index, photo ->
                    DietPhotoThumbnail(
                        path = viewModel.photoFile(photo.relativePath)?.path,
                        onRemove = { viewModel.removePhoto(index) },
                        onPreview = { previewFile = it },
                    )
                }
                if (state.canAddPhoto) {
                    OutlinedButton(
                        onClick = { showPhotoSource = true },
                        modifier = Modifier.size(92.dp).testTag("diet_add_photo"),
                        contentPadding = PaddingValues(6.dp),
                    ) {
                        Text("📷\n添加", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            AiCalorieEstimateAction(
                recordType = state.recordType,
                photoCount = state.photos.size,
                isBusy = state.isGeneratingEstimate,
                onEstimate = viewModel::openEstimateConfirmation,
            )
            OutlinedTextField(
                value = state.finalCaloriesText,
                onValueChange = { value -> viewModel.update { copy(finalCaloriesText = value.filter(Char::isDigit)) } },
                label = { Text("本次总热量（可选，kcal）") },
                modifier = Modifier.fillMaxWidth().testTag("diet_final_calories"),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = { value -> viewModel.update { copy(note = value) } },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
            )
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(
                onClick = {
                    templateName = state.beverageName.ifBlank { state.description }.take(24)
                    templateWithPhotos = false
                    showSaveTemplate = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("存为模板") }
            if (isEditing) {
                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("diet_delete_bottom"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除这条记录") }
            } else {
                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("diet_save"),
                ) { Text(if (state.isSaving) "正在保存…" else "保存") }
            }
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
        HabitDatePickerDialog(
            selectedDate = state.date,
            onConfirm = { selected ->
                viewModel.update { withDate(selected) }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            title = "选择发生日期",
        )
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
    if (showPhotoSource) {
        AlertDialog(
            onDismissRequest = { showPhotoSource = false },
            title = { Text("添加照片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            showPhotoSource = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🖼️ 从相册选择") }
                    OutlinedButton(
                        onClick = {
                            showPhotoSource = false
                            viewModel.createCameraTarget()?.let {
                                cameraTarget = it
                                cameraLauncher.launch(it.contentUri)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("📷 拍照") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPhotoSource = false }) { Text("取消") } },
        )
    }
    if (showSaveTemplate) {
        AlertDialog(
            onDismissRequest = { showSaveTemplate = false },
            title = { Text("保存饮食模板") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("模板名称") },
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("包含照片", modifier = Modifier.weight(1f))
                        Switch(checked = templateWithPhotos, onCheckedChange = { templateWithPhotos = it })
                    }
                    Text("关闭时只保存文字和点单属性。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveAsTemplate(templateName, templateWithPhotos) { showSaveTemplate = false }
                    },
                    enabled = templateName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplate = false }) { Text("取消") } },
        )
    }
    if (showCreateCategory) {
        AlertDialog(
            onDismissRequest = { showCreateCategory = false },
            title = { Text("新建${state.recordType.displayName()}分类") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createDietCategory(newCategoryName) {
                            newCategoryName = ""
                            showCreateCategory = false
                        }
                    },
                    enabled = newCategoryName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showCreateCategory = false }) { Text("取消") } },
        )
    }
    previewFile?.let { file -> DietPhotoPreviewDialog(file) { previewFile = null } }
    if (state.isEstimateSheetVisible) {
        AiCalorieEstimateSheet(
            mealName = state.description.ifBlank { "未命名餐食" },
            photos = state.photos.map { photo ->
                AiEstimatePhotoUi(photo.relativePath, viewModel.photoFile(photo.relativePath))
            },
            selectedPhotoPaths = state.selectedEstimatePhotoPaths,
            estimate = state.estimatePreview,
            selectedPhotoCount = state.selectedEstimatePhotoPaths.size,
            adoptedCaloriesText = state.estimateAdoptedCaloriesText,
            isBusy = state.isGeneratingEstimate,
            errorMessage = state.message,
            onTogglePhoto = viewModel::toggleEstimatePhoto,
            onConfirmPhotos = { viewModel.confirmEstimatePhotos() },
            onAdoptedCaloriesChange = viewModel::updateEstimateAdoptedCalories,
            onCancel = viewModel::cancelEstimateFlow,
            onAdopt = { viewModel.adoptEstimatePreview() },
        )
    }
}

@Composable
private fun DietPhotoThumbnail(
    path: String?,
    onRemove: () -> Unit,
    onPreview: (File) -> Unit,
) {
    val file = remember(path) { path?.let(::File)?.takeIf(File::isFile) }
    val bitmap = remember(path) {
        file?.let {
            BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    }
    Box(
        modifier = Modifier
            .size(92.dp)
            .then(if (bitmap != null && file != null) Modifier.clickable { onPreview(file) } else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "饮食照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        FilledTonalIconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
        ) { Text("×") }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MealFields(state: DietEditorUiState, viewModel: DietEditorViewModel) {
    Text("餐次", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        mealTypeDisplayOrder.forEach { type ->
            FilterChip(state.mealType == type, { viewModel.update { copy(mealType = type) } }, { Text(type.displayName()) })
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
    DietField(state.beverageName, "饮品名称 *", "diet_beverage_name") { viewModel.update { copy(beverageName = it) } }
    DietField(state.brandOrStore, "品牌 / 门店") { viewModel.update { copy(brandOrStore = it) } }
    DietField(state.sizeOrVolume, "杯型 / 容量") { viewModel.update { copy(sizeOrVolume = it) } }
    DietField(state.temperature, "温度") { viewModel.update { copy(temperature = it) } }
    DietField(state.sweetness, "甜度") { viewModel.update { copy(sweetness = it) } }
    DietField(state.toppings, "加料（用顿号分隔）") { viewModel.update { copy(toppings = it) } }
    DietField(state.cupCountText, "杯数") { viewModel.update { copy(cupCountText = it.filter(Char::isDigit)) } }
}

@Composable
private fun DietField(value: String, label: String, tag: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().then(if (tag == null) Modifier else Modifier.testTag(tag)))
}
