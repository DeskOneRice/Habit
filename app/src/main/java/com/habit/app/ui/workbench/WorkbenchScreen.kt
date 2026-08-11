package com.habit.app.ui.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.ui.components.DeviceDateRefreshEffect
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.HabitTopAction
import com.habit.app.ui.components.NavigationMode
import com.habit.app.ui.components.habitEmoji
import com.habit.app.domain.time.HabitTimePolicy
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val workbenchDateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")
private val weeklyInsightStartFormatter = DateTimeFormatter.ofPattern("M月d日")
private val weeklyInsightEndFormatter = DateTimeFormatter.ofPattern("M月d日")
private val weeklyInsightGeneratedFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@Composable
fun WorkbenchScreen(
    viewModel: WorkbenchViewModel,
    onOpenDrawer: () -> Unit,
    onCreateHabit: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenDiet: () -> Unit,
    onAddDiet: () -> Unit,
    onUseDietTemplate: (Long) -> Unit,
    onOpenWeeklyReport: (Long?) -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceDateRefreshEffect(viewModel::refreshDeviceDate)

    Column(
        Modifier.fillMaxSize()
            .testTag("workbench_screen"),
    ) {
        HabitTopAppBar("今日 · ${state.today.format(workbenchDateFormatter)}", NavigationMode.MENU, onOpenDrawer) {
            HabitTopAction(
                text = "＋",
                contentDescription = "新建习惯",
                onClick = onCreateHabit,
                modifier = Modifier.testTag("workbench_create"),
                textStyle = MaterialTheme.typography.titleLarge,
            )
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { ProgressCard(state) }
                if (state.habits.isEmpty()) {
                    item { EmptyWorkbench(onCreateHabit) }
                } else {
                    item { Text("今天想完成什么", style = MaterialTheme.typography.titleMedium) }
                    items(state.habits, key = { it.habit.id }) { item ->
                        HabitCheckRow(
                            item = item,
                            toggling = item.habit.id in state.togglingHabitIds,
                            onOpen = { onOpenHabit(item.habit.id) },
                            onToggle = { viewModel.toggle(item.habit.id) },
                        )
                    }
                }
                item { DietTodayCard(state, onOpenDiet, onAddDiet) }
                if (state.quickDietTemplates.isNotEmpty()) {
                    item {
                        HabitCard(Modifier.fillMaxWidth()) {
                            Text("饮食快捷记录", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            state.quickDietTemplates.forEach { template ->
                                TextButton(
                                    onClick = { onUseDietTemplate(template.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (template.draft.recordType.name == "BEVERAGE") "☕" else "🍱")
                                    Text(template.name, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                    Text("记录")
                                }
                            }
                        }
                    }
                }
                item { RecentWeekCard(state.recentDays, state.today, onOpenCalendar) }
                state.weeklyInsight?.let { summary ->
                    item { WeeklyInsightCard(summary, onOpenWeeklyReport, onOpenAiSettings) }
                }
                item {
                    HabitCard(Modifier.fillMaxWidth()) {
                        Text("本月数据", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Stat("${state.monthStats.activeDays}", "活跃天数")
                            Stat("${(state.monthStats.completionRate * 100).roundToInt()}%", "完成率")
                            Stat("${state.longestCurrentStreak}", "连续天数")
                        }
                    }
                }
                state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
internal fun WeeklyInsightCard(
    summary: WeeklyInsightSummary,
    onOpenReport: (Long?) -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    HabitCard(Modifier.fillMaxWidth().testTag("weekly_insight_card")) {
        Text("综合周报", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(5.dp))
        Text(
            when (summary.status) {
                WeeklyInsightStatus.NEEDS_MODEL -> "先配置周报模型"
                WeeklyInsightStatus.READY_TO_GENERATE -> "上周数据已准备好"
                WeeklyInsightStatus.SAVED -> summary.title ?: "上周周报已保存"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${summary.startDate.format(weeklyInsightStartFormatter)} – ${summary.endDate.format(weeklyInsightEndFormatter)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        summary.generatedAt?.let { generatedAt ->
            Text(
                "生成于 ${Instant.ofEpochMilli(generatedAt).atZone(HabitTimePolicy.zoneId).format(weeklyInsightGeneratedFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        summary.overview?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (summary.status == WeeklyInsightStatus.NEEDS_MODEL) onOpenModelSettings()
                else onOpenReport(summary.detailStartEpochDay)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("weekly_insight_action"),
        ) {
            Text(
                when (summary.status) {
                    WeeklyInsightStatus.NEEDS_MODEL -> "配置模型"
                    WeeklyInsightStatus.READY_TO_GENERATE -> "生成上周周报"
                    WeeklyInsightStatus.SAVED -> "查看周报"
                },
            )
        }
    }
}

@Composable
private fun DietTodayCard(state: WorkbenchUiState, onOpenDiet: () -> Unit, onAddDiet: () -> Unit) {
    HabitCard(Modifier.fillMaxWidth().clickable(onClick = onOpenDiet)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("☕", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("今日饮食", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.dietRecordCount == 0) "还没有记录" else "${state.dietRecordCount} 条 · ${state.dietCalories?.let { "$it kcal" } ?: "未记热量"}${if (state.beverageCups > 0) " · ${state.beverageCups} 杯饮品" else ""}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAddDiet) { Text("记录") }
        }
    }
}

@Composable
private fun ProgressCard(state: WorkbenchUiState) {
    HabitCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { state.progress }, modifier = Modifier.size(74.dp), strokeWidth = 7.dp)
                Text("${(state.progress * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("今天完成 ${state.completedCount}/${state.totalCount}", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (state.longestCurrentStreak > 0) "最长正在坚持 ${state.longestCurrentStreak} 天" else "从今天开始积累",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HabitCheckRow(
    item: WorkbenchHabitItem,
    toggling: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    HabitCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(habitEmoji(item.habit.iconKey)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.habit.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(item.categoryName.takeIf(String::isNotBlank), "连续 ${item.currentStreak} 天").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = onToggle,
                enabled = !toggling,
                modifier = Modifier
                    .testTag("workbench_toggle_${item.habit.id}")
                    .semantics { contentDescription = if (item.checked) "取消${item.habit.name}打卡" else "完成${item.habit.name}打卡" },
            ) { Text(if (item.checked) "✓" else "○") }
        }
    }
}

@Composable
private fun RecentWeekCard(days: List<RecentDay>, today: java.time.LocalDate, onOpenCalendar: () -> Unit) {
    HabitCard(Modifier.fillMaxWidth().testTag("recent_week_strip")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("最近 7 天", style = MaterialTheme.typography.titleMedium)
            TextButton(onOpenCalendar) { Text("打开日历") }
        }
        RecentWeekTimeline(days = days, today = today)
    }
}

@Composable
private fun EmptyWorkbench(onCreateHabit: () -> Unit) {
    HabitCard(Modifier.fillMaxWidth()) {
        Text("🌤️", style = MaterialTheme.typography.headlineMedium)
        Text("今天还没有安排习惯", style = MaterialTheme.typography.titleMedium)
        Text("创建一个很小的行动，让仪式感从今天开始。")
        Spacer(Modifier.height(12.dp))
        Button(onCreateHabit) { Text("新建习惯") }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
