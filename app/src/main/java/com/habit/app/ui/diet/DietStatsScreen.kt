package com.habit.app.ui.diet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.DietDailyTotal
import com.habit.app.domain.model.DietRangeSummary
import com.habit.app.domain.model.RankedValue
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.LocalDate

@Composable
fun DietStatsScreen(viewModel: DietStatsViewModel, onOpenDrawer: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    Scaffold(topBar = { HabitTopAppBar("饮食统计", NavigationMode.MENU, onOpenDrawer) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("diet_stats"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatsRangeSelector(state.range, viewModel::selectRange) }
            item { StatsOverview(summary) }
            item { TrendCard(summary, state.range) }
            item { CompositionCard(summary) }
            item { RankingCard("饮品分类", summary.categoryRanking) }
            item { RankingCard("温度偏好", summary.temperatureRanking) }
            item { RankingCard("常点品牌", summary.brandRanking) }
            item { RankingCard("甜度偏好", summary.sweetnessRanking) }
        }
    }
}

@Composable
private fun StatsRangeSelector(selected: DietStatsRange, onSelected: (DietStatsRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .padding(4.dp),
    ) {
        DietStatsRange.entries.forEach { range ->
            val active = range == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(15.dp),
                    )
                    .clickable { onSelected(range) }
                    .testTag(if (range == DietStatsRange.SEVEN_DAYS) "diet_stats_7" else "diet_stats_30"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    range.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsOverview(summary: DietRangeSummary) {
    val values = listOf(
        summary.recordCount to "记录",
        summary.recordedDays to "记录天数",
        summary.mealRecordCount to "餐食",
        summary.beverageRecordCount to "饮品",
    )
    HabitCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            values.forEachIndexed { index, (value, label) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (index < values.lastIndex) {
                        Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCard(summary: DietRangeSummary, range: DietStatsRange) {
    val buckets = chartBuckets(summary.dailyTotals, range)
    HabitCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("每日趋势", style = MaterialTheme.typography.titleMedium)
            Text(
                summary.totalCalories?.let { "合计 $it kcal" } ?: "暂无热量记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (buckets.all { it.value == 0 }) {
            EmptyStats("记录饮食后会显示每日趋势")
        } else {
            TrendChart(buckets)
        }
    }
}

@Composable
private fun TrendChart(buckets: List<ChartBucket>) {
    val primary = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.primaryContainer
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val max = buckets.maxOfOrNull(ChartBucket::value)?.coerceAtLeast(1) ?: 1
    Canvas(Modifier.fillMaxWidth().height(132.dp)) {
        val chartHeight = size.height - 24.dp.toPx()
        val slot = size.width / buckets.size.coerceAtLeast(1)
        drawLine(axisColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
        val line = Path()
        buckets.forEachIndexed { index, bucket ->
            val ratio = bucket.value.toFloat() / max
            val barHeight = chartHeight * ratio
            val width = slot * 0.42f
            val left = index * slot + (slot - width) / 2f
            val top = chartHeight - barHeight
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(width, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            )
            val point = Offset(index * slot + slot / 2f, top)
            if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
            drawCircle(primary, radius = 2.5.dp.toPx(), center = point)
        }
        drawPath(line, primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
    Row(Modifier.fillMaxWidth()) {
        buckets.forEach { bucket ->
            Text(
                bucket.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CompositionCard(summary: DietRangeSummary) {
    HabitCard(Modifier.fillMaxWidth()) {
        Text("记录构成", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (summary.recordCount == 0) {
            EmptyStats("记录更多后会显示餐食与饮品构成")
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val mealColor = MaterialTheme.colorScheme.primary
                val drinkColor = MaterialTheme.colorScheme.tertiary
                val emptyRingColor = MaterialTheme.colorScheme.surfaceVariant
                Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 15.dp.toPx()
                        val mealSweep = summary.mealRecordCount.toFloat() / summary.recordCount * 360f
                        drawArc(
                            color = emptyRingColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        if (mealSweep > 0f) drawArc(mealColor, -90f, mealSweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
                        if (mealSweep < 360f) drawArc(drinkColor, -90f + mealSweep, 360f - mealSweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(summary.recordCount.toString(), style = MaterialTheme.typography.titleLarge)
                        Text("条记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendValue("餐食", summary.mealRecordCount, mealColor)
                    LegendValue("饮品", summary.beverageRecordCount, drinkColor)
                }
            }
        }
    }
}

@Composable
private fun LegendValue(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(50)))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RankingCard(title: String, values: List<RankedValue>) {
    HabitCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        if (values.isEmpty()) {
            EmptyStats("记录更多后会显示统计")
        } else {
            val max = values.maxOf(RankedValue::count).coerceAtLeast(1)
            values.take(5).forEach { value ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(value.label, modifier = Modifier.widthIn(min = 58.dp, max = 84.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))) {
                        Box(
                            Modifier
                                .fillMaxWidth(value.count.toFloat() / max)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                        )
                    }
                    Text(value.count.toString(), modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun EmptyStats(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private data class ChartBucket(val label: String, val value: Int)

private fun chartBuckets(values: List<DietDailyTotal>, range: DietStatsRange): List<ChartBucket> {
    if (range == DietStatsRange.SEVEN_DAYS) {
        val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
        return values.map { total ->
            ChartBucket(weekLabels[LocalDate.ofEpochDay(total.epochDay).dayOfWeek.value - 1], total.totalCalories ?: 0)
        }
    }
    return values.chunked(6).map { group ->
        val first = LocalDate.ofEpochDay(group.first().epochDay).dayOfMonth
        val last = LocalDate.ofEpochDay(group.last().epochDay).dayOfMonth
        ChartBucket("$first–$last", group.sumOf { it.totalCalories ?: 0 })
    }
}
