package com.habit.app.ui.diet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.DietRecordType

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
    estimate: AiCalorieEstimateDraft?,
    selectedPhotoCount: Int,
    adoptedCaloriesText: String,
    isBusy: Boolean,
    errorMessage: String?,
    onAdoptedCaloriesChange: (String) -> Unit,
    onCancel: () -> Unit,
    onAdopt: (Int) -> Unit,
) {
    val adoptedCalories = adoptedCaloriesText.toIntOrNull()
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
            Text("AI 估算热量", style = MaterialTheme.typography.headlineSmall)
            Text(
                "已选择 $selectedPhotoCount 张照片",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            if (estimate != null && !isBusy) {
                Button(
                    onClick = { adoptedCalories?.let(onAdopt) },
                    enabled = adoptedCalories != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_estimate_adopt"),
                ) { Text("采用 ${adoptedCaloriesText.ifBlank { "—" }} kcal") }
            }
        }
    }
}
