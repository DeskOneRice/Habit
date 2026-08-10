package com.habit.app.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.model.AiWeeklyReportDraft
import com.habit.app.domain.model.WeeklyReportCoverage
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAction
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

sealed interface WeeklySuggestionPresentation {
    data class Valid(val items: List<String>) : WeeklySuggestionPresentation
    data object Incomplete : WeeklySuggestionPresentation
}

internal fun presentWeeklySuggestions(suggestions: List<String>): WeeklySuggestionPresentation {
    if (suggestions.size != 3) return WeeklySuggestionPresentation.Incomplete
    val normalized = suggestions.mapIndexed { index, suggestion ->
        normalizeSuggestion(suggestion, index + 1)
    }
    return if (normalized.any(String::isBlank)) WeeklySuggestionPresentation.Incomplete
    else WeeklySuggestionPresentation.Valid(normalized)
}

@Composable
fun AiWeeklyReportScreen(
    viewModel: AiWeeklyReportViewModel,
    navigationMode: NavigationMode,
    onNavigation: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSavedReport: (Long) -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val replacement by viewModel.replacementRequests.collectAsStateWithLifecycle()
    val busy = state is AiWeeklyReportState.Generating || state is AiWeeklyReportState.Saving
    var showGenerateConfirmation by remember { mutableStateOf(false) }
    BackHandler(enabled = busy) { }

    Box(Modifier.fillMaxSize().testTag("weekly_report_root")) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (busy) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            HabitTopAppBar("综合周报", navigationMode, { if (!busy) onNavigation() }) {
                HabitTopAction(
                    text = "历史",
                    contentDescription = "查看历史周报",
                    onClick = { if (!busy) onOpenHistory() },
                    modifier = Modifier.testTag("weekly_report_history"),
                    enabled = !busy,
                )
            }
            when (val current = state) {
                AiWeeklyReportState.LoadingLocalData -> LoadingLocalData()
                is AiWeeklyReportState.ReadyToGenerate -> ReadyToGenerateContent(
                    state = current,
                    onGenerate = { if (!busy) showGenerateConfirmation = true },
                    onOpenSaved = { if (!busy) onOpenSavedReport(it.startEpochDay) },
                )
                is AiWeeklyReportState.Generating -> {
                    StableReportContent(current.recoverTo, onGenerate = {}, onOpenSaved = {})
                }
                is AiWeeklyReportState.Preview -> PreviewContent(current, viewModel::save)
                is AiWeeklyReportState.Saving -> {
                    PreviewContent(current.preview, onSave = {})
                }
                is AiWeeklyReportState.Saved -> AiWeeklyReportDocument(current.report)
                is AiWeeklyReportState.Error -> ErrorContent(
                    error = current,
                    onRetry = viewModel::retry,
                    onOpenModelSettings = { if (!busy) onOpenModelSettings() },
                )
            }
        }
        if (busy) {
            val generating = state is AiWeeklyReportState.Generating
            LoadingOverlay(
                label = if (generating) "正在生成周报…" else "正在保存周报…",
                onCancel = if (generating) viewModel::cancelGeneration else null,
            )
        }
    }

    if (showGenerateConfirmation) {
        AlertDialog(
            onDismissRequest = { showGenerateConfirmation = false },
            title = { Text("生成上周综合周报？") },
            text = { Text("将发送上周的结构化习惯与饮食摘要给已绑定模型，不包含照片、Key 或其他本地隐私数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGenerateConfirmation = false
                        viewModel.generate()
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("weekly_report_generate_confirm"),
                ) { Text("确认生成") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGenerateConfirmation = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("取消") }
            },
        )
    }

    if (replacement != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReplacement,
            title = { Text("替换已有周报？") },
            text = { Text("这一周已经保存过周报。确认后将以当前预览替换旧版本，其他周报不会受影响。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmReplacement() },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("weekly_report_replace_confirm"),
                ) { Text("确认替换") }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissReplacement,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("保留旧周报") }
            },
        )
    }
}

@Composable
private fun LoadingLocalData() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("正在整理上周数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReadyToGenerateContent(
    state: AiWeeklyReportState.ReadyToGenerate,
    onGenerate: () -> Unit,
    onOpenSaved: (AiWeeklyReport) -> Unit,
) {
    val week = formatWeek(state.input.startEpochDay, state.input.endEpochDay)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HabitCard(Modifier.fillMaxWidth()) {
                Text("上一完整周", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(week, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "从本地习惯完成情况和饮食记录中提炼一份结构化回顾。生成前会再次确认。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("weekly_report_generate"),
                ) { Text(if (state.existingReport == null) "生成上周周报" else "重新生成上周周报") }
            }
        }
        state.existingReport?.let { report ->
            item {
                HabitCard(Modifier.fillMaxWidth()) {
                    Text("已保存版本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(report.title, style = MaterialTheme.typography.titleLarge)
                    Text(report.overview, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { onOpenSaved(report) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("查看只读详情") }
                }
            }
        }
    }
}

@Composable
private fun StableReportContent(
    state: AiWeeklyReportState,
    onGenerate: () -> Unit,
    onOpenSaved: (Long) -> Unit,
) {
    when (state) {
        is AiWeeklyReportState.ReadyToGenerate -> ReadyToGenerateContent(state, onGenerate) { onOpenSaved(it.startEpochDay) }
        is AiWeeklyReportState.Preview -> PreviewContent(state, onSave = {})
        else -> Unit
    }
}

@Composable
private fun PreviewContent(state: AiWeeklyReportState.Preview, onSave: () -> Unit) {
    WeeklyReportDocument(
        document = state.draft.toDocument(),
        bottomContent = {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("weekly_report_save"),
            ) { Text(if (state.existingReport == null) "保存周报" else "保存并替换旧版本") }
        },
    )
}

@Composable
private fun ErrorContent(
    error: AiWeeklyReportState.Error,
    onRetry: () -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HabitCard(Modifier.fillMaxWidth()) {
                Text("本次操作未完成", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(error.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("返回重试") }
                if (error.failure == AiWeeklyReportFailure.ModelUnavailable || error.failure == AiWeeklyReportFailure.KeyUnavailable) {
                    TextButton(
                        onClick = onOpenModelSettings,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("打开模型配置") }
                }
            }
        }
        error.recoverTo.existingReportOrNull()?.let { report ->
            item {
                HabitCard(Modifier.fillMaxWidth()) {
                    Text("旧周报仍已安全保存", style = MaterialTheme.typography.titleMedium)
                    Text(report.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay(label: String, onCancel: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10f)
            .testTag("weekly_report_loading_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
                .clearAndSetSemantics { contentDescription = label },
        )
        Surface(shape = MaterialTheme.shapes.extraLarge, shadowElevation = 8.dp) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(Modifier.clearAndSetSemantics { })
                Spacer(Modifier.height(12.dp))
                Text(label, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleMedium)
                onCancel?.let {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = it,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("weekly_report_cancel_generation"),
                    ) { Text("取消生成") }
                }
            }
        }
    }
}

@Composable
internal fun AiWeeklyReportDocument(report: AiWeeklyReport, onRegenerate: (() -> Unit)? = null) {
    WeeklyReportDocument(report.toDocument(), onRegenerate = onRegenerate)
}

@Composable
private fun WeeklyReportDocument(
    document: WeeklyReportDocument,
    onRegenerate: (() -> Unit)? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HabitCard(Modifier.fillMaxWidth().testTag("weekly_report_header")) {
                    Text(formatWeek(document.startEpochDay, document.endEpochDay), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(document.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(document.overview, style = MaterialTheme.typography.bodyLarge)
                }
                MetricsCard(document.coverage)
                AnalysisCard("习惯", "习惯分析", document.habitAnalysis, "weekly_report_habit")
                AnalysisCard("饮食", "饮食分析", document.dietAnalysis, "weekly_report_diet")
                AnalysisCard("关联", "习惯与饮食关联", document.correlationFinding, "weekly_report_correlation")
                HabitCard(Modifier.fillMaxWidth().testTag("weekly_report_suggestions")) {
                    Text("下周建议", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    when (val suggestions = presentWeeklySuggestions(document.suggestions)) {
                        is WeeklySuggestionPresentation.Valid -> suggestions.items.forEachIndexed { index, suggestion ->
                            Row(
                                Modifier.fillMaxWidth().testTag("weekly_report_numbered_suggestion"),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Text(
                                        "${index + 1}",
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(suggestion, Modifier.weight(1f).padding(top = 4.dp))
                            }
                            if (index < 2) Spacer(Modifier.height(10.dp))
                        }
                        WeeklySuggestionPresentation.Incomplete -> {
                            Text(
                                if (onRegenerate == null) "周报建议数据不完整。" else "周报建议数据不完整，请重新生成。",
                                color = MaterialTheme.colorScheme.error,
                            )
                            onRegenerate?.let {
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = it,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("weekly_report_regenerate"),
                                ) { Text("重新生成周报") }
                            }
                        }
                    }
                }
                HabitCard(Modifier.fillMaxWidth().testTag("weekly_report_footer")) {
                    Text("提醒与数据覆盖", style = MaterialTheme.typography.titleLarge)
                    if (document.cautions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        document.cautions.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(coverageText(document.coverage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "AI 生成内容仅供自我回顾，不构成医疗、营养或心理诊断。关联描述不代表因果关系。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bottomContent()
            }
        }
    }
}

@Composable
private fun MetricsCard(coverage: WeeklyReportCoverage) {
    val completion = if (coverage.scheduledHabitCount == 0) 0 else
        (coverage.completedHabitCount * 100f / coverage.scheduledHabitCount).roundToInt()
    HabitCard(Modifier.fillMaxWidth().testTag("weekly_report_metrics")) {
        Text("本地指标", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("$completion%", "习惯完成")
            Metric("${coverage.completedHabitCount}/${coverage.scheduledHabitCount}", "完成/计划")
            Metric("${coverage.dietRecordDays} 天", "饮食覆盖")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "饮食 ${coverage.dietRecordCount} 条 · 已知热量 ${coverage.knownCalorieRecords} 条 · 缺失 ${coverage.missingCalorieRecords} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalysisCard(kicker: String, title: String, body: String, tag: String) {
    HabitCard(Modifier.fillMaxWidth().testTag(tag)) {
        Text(kicker, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class WeeklyReportDocument(
    val startEpochDay: Long,
    val endEpochDay: Long,
    val title: String,
    val overview: String,
    val habitAnalysis: String,
    val dietAnalysis: String,
    val correlationFinding: String,
    val suggestions: List<String>,
    val cautions: List<String>,
    val coverage: WeeklyReportCoverage,
)

private fun AiWeeklyReportDraft.toDocument() = WeeklyReportDocument(
    startEpochDay, endEpochDay, title, overview, habitAnalysis, dietAnalysis,
    correlationFinding, suggestions, cautions, coverage,
)

private fun AiWeeklyReport.toDocument() = WeeklyReportDocument(
    startEpochDay, endEpochDay, title, overview, habitAnalysis, dietAnalysis,
    correlationFinding, suggestions, cautions, coverage,
)

private fun AiWeeklyReportState.existingReportOrNull(): AiWeeklyReport? = when (this) {
    is AiWeeklyReportState.ReadyToGenerate -> existingReport
    is AiWeeklyReportState.Preview -> existingReport
    else -> null
}

private fun formatWeek(startEpochDay: Long, endEpochDay: Long): String {
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = LocalDate.ofEpochDay(endEpochDay)
    return "${start.format(FULL_DATE)} – ${end.format(SHORT_DATE)}"
}

private fun coverageText(coverage: WeeklyReportCoverage): String =
    "覆盖：习惯计划 ${coverage.scheduledHabitCount} 次，完成 ${coverage.completedHabitCount} 次；饮食记录 ${coverage.dietRecordCount} 条，覆盖 ${coverage.dietRecordDays} 天。"

private val FULL_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val SHORT_DATE = DateTimeFormatter.ofPattern("M月d日")
private fun normalizeSuggestion(suggestion: String, itemNumber: Int): String {
    val value = suggestion.trim()
    val chineseNumber = listOf("一", "二", "三")[itemNumber - 1]
    val circledNumber = listOf("①", "②", "③")[itemNumber - 1]
    val exactPrefixes = listOf(
        "($itemNumber)",
        "（$itemNumber）",
        "$itemNumber、",
        "$itemNumber)",
        "$itemNumber）",
        "$chineseNumber、",
        circledNumber,
    )
    exactPrefixes.firstOrNull(value::startsWith)?.let { prefix ->
        return value.removePrefix(prefix).removePrefix("、").trim()
    }
    val dotPrefix = "$itemNumber."
    if (value.startsWith(dotPrefix) && value.getOrNull(dotPrefix.length)?.isDigit() != true) {
        return value.removePrefix(dotPrefix).trim()
    }
    return value
}
