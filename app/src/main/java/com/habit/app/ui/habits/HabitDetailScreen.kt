package com.habit.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.Habit
import com.habit.app.ui.components.DeviceDateRefreshEffect
import com.habit.app.ui.components.habitEmoji
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun HabitDetailScreen(
    viewModel: HabitDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onArchived: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showArchiveConfirmation by remember { mutableStateOf(false) }
    var deleteStage by remember { mutableIntStateOf(0) }

    DeviceDateRefreshEffect(viewModel::refreshDeviceDate)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("habit_detail_screen")
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "返回习惯列表" },
            ) {
                Text("‹")
            }
            Text("习惯详情", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.size(48.dp))
        }

        state.history?.let { snapshot ->
            val habit = snapshot.habit
            HabitIdentity(habit, state.categoryName)
            StatisticsRow(
                total = snapshot.stats.total,
                current = snapshot.stats.currentStreak,
                longest = snapshot.stats.longestStreak,
            )
            HabitMonthSection(
                month = state.visibleMonth,
                habit = habit,
                completedEpochDays = snapshot.completedEpochDays,
                completedCount = state.monthProgress.completedCount,
                expectedCount = state.monthProgress.expectedCount,
                completionRate = state.monthProgress.completionRate,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
            )
            OutlinedButton(
                onClick = { onEdit(habit.id) },
                enabled = !state.actionInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("edit_habit"),
            ) {
                Text("编辑习惯")
            }
            if (habit.archivedEpochDay == null) {
                OutlinedButton(
                    onClick = { showArchiveConfirmation = true },
                    enabled = !state.actionInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("archive_habit"),
                ) {
                    Text("归档习惯")
                }
            } else {
                Text("此习惯已归档，历史记录仍可查看。")
            }
            TextButton(
                onClick = { deleteStage = 1 },
                enabled = !state.actionInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("delete_habit"),
            ) {
                Text("删除习惯")
            }
        }

        if (state.loading) {
            Text("正在加载习惯详情…")
        }
        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("habit_detail_message"),
            )
        }
    }

    if (showArchiveConfirmation) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmation = false },
            title = { Text("归档习惯？") },
            text = { Text("归档后不会出现在当前习惯中，但历史记录会保留。") },
            confirmButton = {
                Button(
                    onClick = {
                        showArchiveConfirmation = false
                        viewModel.archive(onArchived)
                    },
                    modifier = Modifier.testTag("archive_confirm"),
                ) {
                    Text("确认归档")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
    if (deleteStage == 1) {
        AlertDialog(
            onDismissRequest = { deleteStage = 0 },
            title = { Text("删除习惯？") },
            text = {
                Text(
                    "删除后，所有历史打卡记录都会被删除。",
                    Modifier.testTag("delete_history_warning"),
                )
            },
            confirmButton = {
                Button(
                    onClick = { deleteStage = 2 },
                    modifier = Modifier.testTag("delete_continue"),
                ) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteStage = 0 }) {
                    Text("取消")
                }
            },
        )
    }
    if (deleteStage == 2) {
        AlertDialog(
            onDismissRequest = { deleteStage = 0 },
            title = { Text("确认永久删除？") },
            text = { Text("此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteStage = 0
                        viewModel.delete(onDeleted)
                    },
                    modifier = Modifier.testTag("delete_confirm"),
                ) {
                    Text("永久删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteStage = 0 }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun HabitIdentity(habit: Habit, categoryName: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(habitEmoji(habit.iconKey), fontSize = 34.sp)
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.headlineSmall)
                Text(categoryName.ifBlank { "未分类" })
            }
            Box(
                Modifier
                    .size(22.dp)
                    .background(Color(habit.themeColor), CircleShape)
                    .semantics { contentDescription = "习惯识别颜色" },
            )
        }
    }
}

@Composable
private fun StatisticsRow(total: Int, current: Int, longest: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatisticCard("累计完成", total, "stat_total", Modifier.weight(1f))
        StatisticCard("当前连续", current, "stat_current", Modifier.weight(1f))
        StatisticCard("最长连续", longest, "stat_longest", Modifier.weight(1f))
    }
}

@Composable
private fun StatisticCard(
    label: String,
    value: Int,
    tag: String,
    modifier: Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(tag))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HabitMonthSection(
    month: YearMonth,
    habit: Habit,
    completedEpochDays: Set<Long>,
    completedCount: Int,
    expectedCount: Int,
    completionRate: Float,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onPreviousMonth,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("habit_detail_previous_month")
                    .semantics { contentDescription = "上个月" },
            ) {
                Text("‹")
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("habit_detail_month_title"),
            )
            FilledIconButton(
                onClick = onNextMonth,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("habit_detail_next_month")
                    .semantics { contentDescription = "下个月" },
            ) {
                Text("›")
            }
        }
        SingleHabitMonthGrid(
            month = month,
            completedEpochDays = completedEpochDays,
            habitColor = Color(habit.themeColor),
        )
        Text("本月进度 $completedCount / $expectedCount")
        LinearProgressIndicator(
            progress = { completionRate.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("habit_month_progress"),
        )
        Text("完成率 ${(completionRate * 100).roundToInt()}%")
    }
}

@Composable
private fun SingleHabitMonthGrid(
    month: YearMonth,
    completedEpochDays: Set<Long>,
    habitColor: Color,
) {
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(Modifier.fillMaxWidth()) {
        weekdayLabels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }

    val leading = month.atDay(1).dayOfWeek.mondayIndex()
    val cells = buildList<LocalDate?> {
        repeat(leading) { add(null) }
        repeat(month.lengthOfMonth()) { dayIndex -> add(month.atDay(dayIndex + 1)) }
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                if (date == null) {
                    Box(Modifier.weight(1f).aspectRatio(1f))
                } else {
                    HabitDayCell(
                        date = date,
                        completed = date.toEpochDay() in completedEpochDays,
                        habitColor = habitColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            repeat(7 - week.size) {
                Box(Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }
}

@Composable
private fun HabitDayCell(
    date: LocalDate,
    completed: Boolean,
    habitColor: Color,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("habit_day_$date")
            .semantics {
                contentDescription = "${date.monthValue}月${date.dayOfMonth}日"
                stateDescription = if (completed) "已完成" else "未完成"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
        if (completed) {
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(10.dp)
                    .background(habitColor, CircleShape)
                    .testTag("habit_day_${date}_completed")
                    .semantics { contentDescription = "已完成" },
            )
        }
    }
}

private fun DayOfWeek.mondayIndex(): Int = value - DayOfWeek.MONDAY.value
