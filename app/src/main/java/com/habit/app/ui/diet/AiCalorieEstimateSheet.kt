package com.habit.app.ui.diet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.DietRecordType
import java.io.File

internal data class AiEstimatePhotoUi(
    val relativePath: String,
    val file: File?,
)

@Composable
internal fun AiCalorieEstimateAction(
    recordType: DietRecordType,
    photoCount: Int,
    isBusy: Boolean,
    onEstimate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recordType != DietRecordType.MEAL) return
    val hasUsablePhotos = photoCount in 1..3
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            onClick = onEstimate,
            enabled = hasUsablePhotos && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("ai_calorie_estimate"),
        ) {
            Text(if (isBusy) "正在估算…" else "AI 估算热量")
        }
        Text(
            text = if (hasUsablePhotos) "已选择 $photoCount 张照片" else "请先添加 1–3 张餐食照片",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiCalorieEstimateSheet(
    mealName: String = "",
    photos: List<AiEstimatePhotoUi> = emptyList(),
    selectedPhotoPaths: Set<String> = emptySet(),
    estimate: AiCalorieEstimateDraft?,
    selectedPhotoCount: Int = selectedPhotoPaths.size,
    adoptedCaloriesText: String,
    isBusy: Boolean,
    errorMessage: String?,
    onTogglePhoto: (String) -> Unit = {},
    onConfirmPhotos: () -> Unit = {},
    onAdoptedCaloriesChange: (String) -> Unit,
    onCancel: () -> Unit,
    onAdopt: (Int) -> Unit,
) {
    val adoptedCalories = adoptedCaloriesText.toIntOrNull()
    val isPhotoConfirmation = photos.isNotEmpty() && estimate == null && !isBusy && errorMessage.isNullOrBlank()
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        modifier = Modifier.testTag("ai_estimate_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("ai_estimate_content"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isPhotoConfirmation) "发送前确认" else "AI 估算热量", style = MaterialTheme.typography.headlineSmall)
            if (isPhotoConfirmation) {
                Text(
                    mealName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("ai_estimate_meal_name"),
                )
                Text(
                    "已选择 $selectedPhotoCount / ${photos.size} 张照片",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    photos.forEach { photo ->
                        AiEstimateSelectablePhoto(
                            photo = photo,
                            selected = photo.relativePath in selectedPhotoPaths,
                            onClick = { onTogglePhoto(photo.relativePath) },
                        )
                    }
                }
                if (selectedPhotoCount == 0) {
                    Text("请至少选择 1 张照片", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("确认后才会发送所选照片进行识别", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    "已选择 $selectedPhotoCount 张照片",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                isBusy -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(28.dp))
                        Text("正在识别这餐…", style = MaterialTheme.typography.titleMedium)
                    }
                }

                estimate != null -> {
                    Text("识别结果", style = MaterialTheme.typography.titleMedium)
                    estimate.items.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.portion} · ${item.minKcal}–${item.maxKcal} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "总计 ${estimate.totalMinKcal}–${estimate.totalMaxKcal} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = adoptedCaloriesText,
                        onValueChange = { value -> onAdoptedCaloriesChange(value.filter(Char::isDigit)) },
                        label = { Text("采用热量（kcal）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("ai_estimate_adopted_kcal"),
                    )
                    Text(
                        estimate.accuracyNote.ifBlank { "仅用于估算，请按实际份量调整" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                !errorMessage.isNullOrBlank() -> Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ai_estimate_error"),
                )
            }
            Text(
                "照片仅用于本次识别，不会展示密钥、模型标识或原始响应",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_estimate_cancel"),
            ) { Text(if (isBusy) "取消估算" else "取消") }
            if (isPhotoConfirmation) {
                Button(
                    onClick = onConfirmPhotos,
                    enabled = selectedPhotoCount in 1..3,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_estimate_confirm_photos"),
                ) { Text("确认并开始估算") }
            } else if (estimate != null && !isBusy) {
                Button(
                    onClick = { adoptedCalories?.let(onAdopt) },
                    enabled = adoptedCalories != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_estimate_adopt"),
                ) { Text("采用 ${adoptedCaloriesText.ifBlank { "—" }} kcal") }
            }
        }
    }
}

@Composable
private fun AiEstimateSelectablePhoto(
    photo: AiEstimatePhotoUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val targetPx = with(LocalDensity.current) { 88.dp.roundToPx() }
    val bitmapState = rememberDietSampledBitmap(photo.file, targetPx, targetPx)
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(3.dp)
            .testTag("ai_estimate_photo_${photo.relativePath}"),
        contentAlignment = Alignment.Center,
    ) {
        when (bitmapState) {
            DietSampledBitmapState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            DietSampledBitmapState.Failed -> Text("照片")
            is DietSampledBitmapState.Ready -> Image(
                bitmap = bitmapState.bitmap.asImageBitmap(),
                contentDescription = "待估算餐食照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(13.dp)),
            )
        }
        if (selected) {
            Text(
                "✓",
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
