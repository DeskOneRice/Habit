package com.habit.app.ui.diet

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import com.habit.app.domain.time.HabitTimePolicy
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.HabitTopAction
import com.habit.app.ui.components.NavigationMode
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
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("diet_detail_edit"),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("这条饮食记录不存在或已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onBack) { Text("返回") }
                }
            }

            else -> DietDetailContent(
                record = state.record!!,
                categoryName = state.categoryName,
                photoStore = photoStore,
                onRepeat = onRepeat,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DietDetailContent(
    record: MealRecord,
    categoryName: String,
    photoStore: DietPhotoStore,
    onRepeat: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp)
            .testTag("diet_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HabitCard(Modifier.fillMaxWidth()) {
            Text(record.title(), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(record.displayDateTime(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (categoryName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(categoryName, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (record.photos.isNotEmpty()) {
            Text("照片", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(record.photos.sortedBy(DietPhoto::sortOrder), key = DietPhoto::relativePath) { photo ->
                    DetailPhoto(photo, photoStore, record)
                }
            }
        }

        HabitCard(Modifier.fillMaxWidth()) {
            if (record.recordType == DietRecordType.MEAL) {
                DetailValue("餐次", record.mealType.label())
                DetailValue("内容", record.description)
                record.foodItems.sortedBy { it.sortOrder }.forEach { food ->
                    val detail = listOfNotNull(food.portionText, food.calories?.let { "$it kcal" }).joinToString(" · ")
                    DetailValue(food.name, detail)
                }
            } else {
                val drink = record.beverage
                DetailValue("饮品名称", drink?.beverageName.orEmpty())
                DetailValue("品牌 / 门店", drink?.brandOrStore.orEmpty())
                DetailValue("杯型 / 容量", drink?.sizeOrVolume.orEmpty())
                DetailValue("冷热", drink?.temperature.orEmpty())
                DetailValue("冰量", drink?.iceLevel.orEmpty())
                DetailValue("甜度", drink?.sweetness.orEmpty())
                DetailValue("加料", drink?.toppings?.joinToString("、").orEmpty())
                drink?.cupCount?.takeIf { it > 0 }?.let { DetailValue("杯数", it.toString()) }
            }
            record.finalCalories?.let { DetailValue("热量", "$it kcal") }
            DetailValue("备注", record.note)
        }

        Button(
            onClick = { onRepeat(record.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("diet_detail_repeat"),
        ) { Text("再记一次") }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetailPhoto(photo: DietPhoto, photoStore: DietPhotoStore, record: MealRecord) {
    val file = remember(photo.relativePath, photoStore) { runCatching { photoStore.file(photo.relativePath) }.getOrNull() }
    val bitmap = remember(file?.path, file?.lastModified()) {
        file?.takeIf { it.isFile }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
    }
    Box(
        Modifier
            .size(width = 176.dp, height = 132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "饮食照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(defaultDietEmoji(record), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

private fun MealRecord.title(): String = beverage?.beverageName?.takeIf { it.isNotBlank() }
    ?: mealType.label()

private fun MealRecord.displayDateTime(): String {
    val date = LocalDate.ofEpochDay(recordEpochDay).format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
    val time = Instant.ofEpochMilli(occurredAt).atZone(HabitTimePolicy.zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))
    return "$date $time"
}

private fun MealType?.label(): String = when (this) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
    MealType.LATE_NIGHT -> "夜宵"
    MealType.SNACK -> "加餐"
    null -> "饮品"
}
