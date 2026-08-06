package com.habit.app.ui.diet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.format.DateTimeFormatter

@Composable
fun DietDiaryScreen(
    viewModel: DietDiaryViewModel,
    onOpenDrawer: () -> Unit,
    onAdd: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onRepeatRecord: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weekStart = state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value - 1).toLong())
    Scaffold(
        topBar = { HabitTopAppBar("饮食日记", NavigationMode.MENU, onOpenDrawer) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("diet_add")) { Text("＋") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("diet_diary"),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { offset ->
                        val date = weekStart.plusDays(offset.toLong())
                        val selected = date == state.selectedDate
                        Surface(
                            onClick = { viewModel.selectDate(date) },
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp).testTag("diet_day_${date.toEpochDay()}"),
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
            item {
                HabitCard(Modifier.fillMaxWidth()) {
                    Text(state.selectedDate.format(DateTimeFormatter.ofPattern("M 月 d 日")), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("${state.records.size} 条记录", style = MaterialTheme.typography.headlineSmall); Text("餐饮记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(state.totalCalories?.let { "$it kcal" } ?: "未记录热量", style = MaterialTheme.typography.headlineSmall)
                            if (state.beverageCups > 0) Text("饮品 ${state.beverageCups} 杯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!state.isLoading && state.records.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "今天还没有记录\n点右下角开始记一餐",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(state.records, key = MealRecord::id) { record ->
                MealRecordCard(record, onOpenRecord, onRepeatRecord)
            }
        }
    }
}

@Composable
private fun MealRecordCard(record: MealRecord, onOpen: (Long) -> Unit, onRepeat: (Long) -> Unit) {
    HabitCard(Modifier.fillMaxWidth().testTag("diet_record_${record.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (record.recordType == DietRecordType.BEVERAGE) "☕" else mealEmoji(record.mealType), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.beverage?.beverageName?.ifBlank { null } ?: record.mealType.label(), style = MaterialTheme.typography.titleMedium)
                Text(record.description.ifBlank { record.foodItems.joinToString("、") { it.name } }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(record.finalCalories?.let { "$it kcal" } ?: "—")
                Row {
                    TextButton(onClick = { onRepeat(record.id) }) { Text("再记一次") }
                    TextButton(onClick = { onOpen(record.id) }) { Text("查看") }
                }
            }
        }
    }
}

private fun java.time.DayOfWeek.displayName() = listOf("一", "二", "三", "四", "五", "六", "日")[value - 1]
private fun MealType?.label() = when (this) { MealType.BREAKFAST -> "早餐"; MealType.LUNCH -> "午餐"; MealType.DINNER -> "晚餐"; MealType.LATE_NIGHT -> "夜宵"; MealType.SNACK -> "加餐"; null -> "饮品" }
private fun mealEmoji(type: MealType?) = when (type) { MealType.BREAKFAST -> "🥣"; MealType.LUNCH -> "🍱"; MealType.DINNER -> "🍲"; else -> "🍎" }
