package com.habit.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AiReportHistoryScreen(
    reports: List<AiWeeklyReport>,
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag("weekly_report_history_screen")) {
        HabitTopAppBar("历史周报", NavigationMode.BACK, onBack)
        val sorted = reports.sortedWith(
            compareByDescending<AiWeeklyReport> { it.startEpochDay }
                .thenByDescending { it.updatedAt },
        )
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有保存过周报", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.startEpochDay }) { report ->
                    HabitCard(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReport(report.startEpochDay) }
                            .testTag("weekly_history_${report.startEpochDay}"),
                    ) {
                        Text(report.periodLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(5.dp))
                        Text(report.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            report.overview,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${report.modelNameSnapshot} · 只读",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiWeeklyReportDetailScreen(
    report: AiWeeklyReport,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag("weekly_report_detail_screen")) {
        HabitTopAppBar("周报详情", NavigationMode.BACK, onBack)
        AiWeeklyReportDocument(report)
    }
}

private fun AiWeeklyReport.periodLabel(): String {
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = LocalDate.ofEpochDay(endEpochDay)
    return "${start.format(HISTORY_FULL_DATE)} – ${end.format(HISTORY_SHORT_DATE)}"
}

private val HISTORY_FULL_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val HISTORY_SHORT_DATE = DateTimeFormatter.ofPattern("M月d日")
