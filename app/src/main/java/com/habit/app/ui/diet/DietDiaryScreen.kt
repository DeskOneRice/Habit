package com.habit.app.ui.diet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import com.habit.app.domain.time.HabitTimePolicy
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DietDiaryScreen(
    viewModel: DietDiaryViewModel,
    photoStore: DietPhotoStore,
    onOpenDrawer: () -> Unit,
    onAdd: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onRepeatRecord: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { HabitTopAppBar("饮食日记", NavigationMode.MENU, onOpenDrawer) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("diet_add")) { Text("＋") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("diet_diary"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "mode") {
                DiaryModeSelector(state.mode, viewModel::selectMode)
            }
            if (state.mode == DietDiaryMode.RECENT) {
                if (!state.isLoading && state.recentGroups.isEmpty()) {
                    item(key = "recent_empty") {
                        EmptyDiaryMessage("还没有饮食记录\n点右下角开始记一餐")
                    }
                }
                state.recentGroups.forEach { group ->
                    item(key = "date_${group.epochDay}") {
                        Text(
                            LocalDate.ofEpochDay(group.epochDay).format(DATE_HEADER_FORMAT),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(group.records, key = { "recent_${it.id}" }) { record ->
                        MealRecordCard(record, photoStore, onOpenRecord, onRepeatRecord)
                    }
                }
            } else {
                item(key = "week") {
                    DiaryWeekSelector(state.selectedDate, viewModel::selectDate)
                }
                item(key = "summary") {
                    DiaryDaySummary(state)
                }
                if (!state.isLoading && state.records.isEmpty()) {
                    item(key = "day_empty") {
                        EmptyDiaryMessage("今天还没有记录\n点右下角开始记一餐")
                    }
                }
                items(state.records, key = { "day_${it.id}" }) { record ->
                    MealRecordCard(record, photoStore, onOpenRecord, onRepeatRecord)
                }
            }
        }
    }
}

@Composable
private fun DiaryModeSelector(mode: DietDiaryMode, onSelected: (DietDiaryMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == DietDiaryMode.RECENT,
            onClick = { onSelected(DietDiaryMode.RECENT) },
            label = { Text("最近记录") },
            modifier = Modifier.weight(1f).testTag("diet_mode_recent"),
        )
        FilterChip(
            selected = mode == DietDiaryMode.DAY,
            onClick = { onSelected(DietDiaryMode.DAY) },
            label = { Text("按日查看") },
            modifier = Modifier.weight(1f).testTag("diet_mode_day"),
        )
    }
}

@Composable
private fun DiaryWeekSelector(selectedDate: LocalDate, onSelected: (LocalDate) -> Unit) {
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val selected = date == selectedDate
            Surface(
                onClick = { onSelected(date) },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp).testTag("diet_day_${date.toEpochDay()}"),
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(date.dayOfWeek.displayName(), style = MaterialTheme.typography.labelMedium)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DiaryDaySummary(state: DietDiaryUiState) {
    HabitCard(Modifier.fillMaxWidth()) {
        Text(state.selectedDate.format(DateTimeFormatter.ofPattern("M 月 d 日")), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${state.records.size} 条记录", style = MaterialTheme.typography.headlineSmall)
                Text("餐饮记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(state.totalCalories?.let { "$it kcal" } ?: "未记录热量", style = MaterialTheme.typography.headlineSmall)
                if (state.beverageCups > 0) Text("饮品 ${state.beverageCups} 杯", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyDiaryMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MealRecordCard(
    record: MealRecord,
    photoStore: DietPhotoStore,
    onOpen: (Long) -> Unit,
    onRepeat: (Long) -> Unit,
) {
    HabitCard(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(record.id) }
            .testTag("diet_record_${record.id}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DietRecordThumbnail(record, photoStore)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.beverage?.beverageName?.ifBlank { null } ?: record.mealType.label(), style = MaterialTheme.typography.titleMedium)
                val description = record.description.ifBlank { record.foodItems.joinToString("、") { it.name } }
                if (description.isNotBlank()) Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${record.displayTime()} · ${record.finalCalories?.let { "$it kcal" } ?: "未记录热量"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onRepeat(record.id) }) { Text("再记一次") }
                    TextButton(onClick = { onOpen(record.id) }) { Text("查看") }
                }
            }
        }
    }
}

private fun MealRecord.displayTime(): String = Instant.ofEpochMilli(occurredAt)
    .atZone(HabitTimePolicy.zoneId)
    .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun java.time.DayOfWeek.displayName() = listOf("一", "二", "三", "四", "五", "六", "日")[value - 1]
private fun MealType?.label() = when (this) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
    MealType.LATE_NIGHT -> "夜宵"
    MealType.SNACK -> "加餐"
    null -> "饮品"
}

private val DATE_HEADER_FORMAT = DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)
