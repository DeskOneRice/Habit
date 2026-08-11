package com.habit.app.ui.diet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.AiCalorieEstimate
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.displayName
import com.habit.app.domain.model.displayTemperature
import com.habit.app.domain.model.displayTitle
import com.habit.app.domain.time.HabitTimePolicy
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAction
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DietRecordDetailScreen(
    viewModel: DietRecordDetailViewModel,
    photoStore: DietPhotoStore,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRepeat: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            HabitTopAppBar("饮食详情", NavigationMode.BACK, onBack) {
                state.record?.let { record ->
                    HabitTopAction(
                        text = "编辑",
                        contentDescription = "编辑饮食记录",
                        onClick = { onEdit(record.id) },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("diet_detail_edit"),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.notFound || state.record == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("这条饮食记录不存在或已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("返回") }
                }
            }

            else -> DietDetailContent(
                record = state.record!!,
                categoryName = state.categoryName,
                photoStore = photoStore,
                aiEvidence = state.aiEvidence,
                onRepeat = onRepeat,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
internal fun DietDetailContent(
    record: MealRecord,
    categoryName: String,
    photoStore: DietPhotoStore,
    aiEvidence: AiCalorieEstimate?,
    onRepeat: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewFile by remember { mutableStateOf<File?>(null) }
    val sortedPhotos = remember(record.photos) { record.photos.sortedBy(DietPhoto::sortOrder) }
    var currentPhotoIndex by rememberSaveable(record.id, sortedPhotos.map(DietPhoto::relativePath)) {
        mutableStateOf(0)
    }
    val safePhotoIndex = currentPhotoIndex.coerceIn(0, maxOf(0, sortedPhotos.lastIndex))
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp)
            .testTag("diet_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DietDetailHero(
            record = record,
            photos = sortedPhotos,
            currentIndex = safePhotoIndex,
            photoStore = photoStore,
            onPrevious = {
                currentPhotoIndex = if (safePhotoIndex == 0) sortedPhotos.lastIndex else safePhotoIndex - 1
            },
            onNext = {
                currentPhotoIndex = if (safePhotoIndex == sortedPhotos.lastIndex) 0 else safePhotoIndex + 1
            },
            onPreview = { previewFile = it },
        )
        DietDetailHeader(record, categoryName)
        DietFactGrid(record)
        if (record.recordType == DietRecordType.MEAL) {
            UserFoodCard(record)
        } else {
            BeverageAttributeCard(record.beverage)
        }
        aiEvidence?.let { AiEstimateEvidenceCard(it, record.calorieSource) }
        if (record.note.isNotBlank()) DietNoteCard(record.note)
        Button(
            onClick = { onRepeat(record.id) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("diet_detail_repeat"),
        ) { Text("再记一次") }
    }
    previewFile?.let { file -> DietPhotoPreviewDialog(file) { previewFile = null } }
}

@Composable
private fun DietDetailHero(
    record: MealRecord,
    photos: List<DietPhoto>,
    currentIndex: Int,
    photoStore: DietPhotoStore,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPreview: (File) -> Unit,
) {
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 112.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("diet_detail_empty_hero"),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(defaultDietEmoji(record), style = MaterialTheme.typography.headlineMedium)
                Text("暂无照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val photo = photos[currentIndex]
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val targetHeight = with(density) { 220.dp.roundToPx() }
    val file = remember(photo.relativePath, photoStore) {
        runCatching { photoStore.file(photo.relativePath) }.getOrNull()
    }
    val bitmapState = rememberDietSampledBitmap(file, targetWidth, targetHeight)
    val readyBitmap = (bitmapState as? DietSampledBitmapState.Ready)?.bitmap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.dp))
            .then(if (readyBitmap != null && file != null) Modifier.clickable { onPreview(file) } else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("diet_detail_hero"),
        contentAlignment = Alignment.Center,
    ) {
        when (bitmapState) {
            DietSampledBitmapState.Loading -> CircularProgressIndicator()
            DietSampledBitmapState.Failed -> Text(defaultDietEmoji(record), style = MaterialTheme.typography.displaySmall)
            is DietSampledBitmapState.Ready -> Image(
                bitmap = bitmapState.bitmap.asImageBitmap(),
                contentDescription = "饮食照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (photos.size > 1) {
            FilledTonalIconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(8.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("diet_detail_photo_previous"),
            ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
            FilledTonalIconButton(
                onClick = onNext,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("diet_detail_photo_next"),
            ) { Text("›", style = MaterialTheme.typography.headlineMedium) }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            ) {
                Text(
                    "${currentIndex + 1} / ${photos.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("diet_detail_photo_position"),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun DietDetailHeader(record: MealRecord, categoryName: String) {
    HabitCard(Modifier.fillMaxWidth().testTag("diet_detail_header")) {
        Text(record.displayTitle(), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            detailType(record, categoryName),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(record.displayDateTime(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DietFactGrid(record: MealRecord) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("diet_detail_facts"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DietFact("热量", record.finalCalories?.let { "$it kcal" } ?: "未记录", Modifier.weight(1f))
            DietFact(
                if (record.recordType == DietRecordType.MEAL) "餐次" else "杯数",
                if (record.recordType == DietRecordType.MEAL) {
                    record.mealType?.displayName().orEmpty().ifBlank { "未填写" }
                } else {
                    record.beverage?.cupCount?.takeIf { it > 0 }?.toString() ?: "未填写"
                },
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DietFact("照片", "${record.photos.size} 张", Modifier.weight(1f))
            DietFact(
                "记录类型",
                record.recordType.displayName(),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DietFact(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UserFoodCard(record: MealRecord) {
    HabitCard(Modifier.fillMaxWidth().testTag("diet_detail_food_card")) {
        Text("记录的食物", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (record.foodItems.isEmpty()) {
            Text("没有单独填写食物明细", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            record.foodItems.sortedBy { it.sortOrder }.forEach { food ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(food.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(food.portionText, food.calories?.let { "$it kcal" }).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BeverageAttributeCard(beverage: BeverageDetails?) {
    HabitCard(Modifier.fillMaxWidth().testTag("diet_detail_beverage_card")) {
        Text("饮品属性", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        DetailLine("名称", beverage?.beverageName.orEmpty())
        DetailLine("品牌 / 门店", beverage?.brandOrStore.orEmpty())
        DetailLine("杯型 / 容量", beverage?.sizeOrVolume.orEmpty())
        DetailLine("温度", beverage?.displayTemperature().orEmpty())
        DetailLine("甜度", beverage?.sweetness.orEmpty())
        DetailLine("加料", beverage?.toppings?.joinToString("、").orEmpty())
    }
}

@Composable
private fun AiEstimateEvidenceCard(estimate: AiCalorieEstimate, calorieSource: CalorieSource) {
    HabitCard(Modifier.fillMaxWidth().testTag("diet_detail_ai_evidence")) {
        Text("AI 热量依据", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(
            "已采用 ${estimate.adoptedKcal} kcal",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("diet_detail_ai_adopted"),
        )
        Text(
            "估算总范围 ${estimate.totalMinKcal}–${estimate.totalMaxKcal} kcal",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        EvidenceMetaLine("最终来源", calorieSource.displayEvidenceSource())
        EvidenceMetaLine("采用状态", if (estimate.wasModified) "手动修正" else "未修改")
        EvidenceMetaLine("估算模型", estimate.modelNameSnapshot.ifBlank { "未记录" })
        EvidenceMetaLine("生成时间", estimate.displayGeneratedAt())
        Spacer(Modifier.height(8.dp))
        estimate.items.forEach { item ->
            DetailLine(item.name, "${item.portion} · ${item.minKcal}–${item.maxKcal} kcal")
        }
        if (estimate.accuracyNote.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                estimate.accuracyNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvidenceMetaLine(label: String, value: String) {
    Text(
        "$label  $value",
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DietNoteCard(note: String) {
    HabitCard(Modifier.fillMaxWidth().testTag("diet_detail_note")) {
        Text("备注", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(note)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.35f), fontWeight = FontWeight.Medium)
    }
}

private fun detailType(record: MealRecord, categoryName: String): String =
    listOf(record.recordType.displayName(), categoryName.trim())
        .filter(String::isNotBlank)
        .joinToString(" · ")

private fun MealRecord.displayDateTime(): String {
    val date = LocalDate.ofEpochDay(recordEpochDay)
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
    val time = Instant.ofEpochMilli(occurredAt)
        .atZone(HabitTimePolicy.zoneId)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    return "$date $time"
}

private fun CalorieSource.displayEvidenceSource(): String = when (this) {
    CalorieSource.AI_ESTIMATE -> "AI 估算"
    CalorieSource.MANUAL -> "手动记录"
    CalorieSource.ITEM_SUM -> "食物明细合计"
    CalorieSource.NONE -> "未记录"
}

private fun AiCalorieEstimate.displayGeneratedAt(): String = "北京时间 " +
    Instant.ofEpochMilli(generatedAt)
        .atZone(HabitTimePolicy.zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
