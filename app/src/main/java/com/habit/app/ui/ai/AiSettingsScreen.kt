package com.habit.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.time.HabitTimePolicy
import com.habit.app.ui.components.HabitAddFab
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun AiSettingsScreen(
    viewModel: AiSettingsViewModel,
    onOpenDrawer: () -> Unit,
    onAddModel: () -> Unit,
    onEditModel: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(
        initialPage = state.activeTab.ordinal,
        pageCount = { AiSettingsTab.entries.size },
    )

    LaunchedEffect(state.activeTab) {
        if (pagerState.currentPage != state.activeTab.ordinal) {
            pagerState.animateScrollToPage(state.activeTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { viewModel.selectTab(AiSettingsTab.entries[it]) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { HabitTopAppBar("模型配置", NavigationMode.MENU, onOpenDrawer) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (state.activeTab == AiSettingsTab.MODELS) {
                HabitAddFab(onAddModel, testTag = "ai_model_add")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                AiSettingsTab.entries.forEach { tab ->
                    val label = if (tab == AiSettingsTab.MODELS) "模型配置" else "功能绑定"
                    Tab(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(label) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                if (page == AiSettingsTab.MODELS.ordinal) {
                    ModelConfigurationPage(state, viewModel, onEditModel)
                } else {
                    FeatureBindingPage(state, viewModel)
                }
            }
        }
    }

    state.deleteImpact?.let { impact ->
        val labels = impact.features.map(::featureLabel)
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("删除模型配置？") },
            text = {
                Text(
                    if (labels.isEmpty()) {
                        "将删除“${impact.modelName}”及本机保存的 API Key。"
                    } else {
                        "${labels.joinToString("、")}将取消绑定；同时删除本机保存的 API Key。"
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    enabled = impact.modelId !in state.deletingModelIds,
                    modifier = Modifier.sizeIn(minHeight = 48.dp).testTag("ai_delete_confirm"),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissDelete,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ModelConfigurationPage(
    state: AiSettingsUiState,
    viewModel: AiSettingsViewModel,
    onEditModel: (Long) -> Unit,
) {
    if (state.models.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("还没有模型配置", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("点击右下角 + 添加 OpenAI 兼容模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(state.models, key = { it.id }) { item ->
            AiModelCard(item, state, viewModel, onEditModel)
        }
    }
}

@Composable
private fun AiModelCard(
    item: AiModelUi,
    state: AiSettingsUiState,
    viewModel: AiSettingsViewModel,
    onEditModel: (Long) -> Unit,
) {
    HabitCard(Modifier.fillMaxWidth().testTag("ai_model_${item.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.config.name, style = MaterialTheme.typography.titleMedium)
                Text(item.config.modelId, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(
                onClick = { onEditModel(item.id) },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) { Text("编辑") }
            TextButton(
                onClick = { viewModel.requestDelete(item.id) },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "删除 ${item.name}" },
            ) { Text("删除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.config.supportsText) AbilityChip("文本")
            if (item.config.supportsVision) AbilityChip("图片")
            AbilityChip(if (item.config.enabled) "已启用" else "已停用")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (item.config.baseUrl.startsWith("http://")) "⚠ 已明确允许 HTTP" else "HTTPS 安全连接",
            color = if (item.config.baseUrl.startsWith("http://")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Key：${item.keySuffix ?: "未配置"}", style = MaterialTheme.typography.bodyMedium)
        item.config.lastTestedAt?.let { testedAt ->
            Text(
                "北京时间 ${formatBeijingTime(testedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.statusMessage.isNotBlank()) {
            Text(item.statusMessage, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (item.config.supportsText) {
                Button(
                    onClick = { viewModel.testText(item.id) },
                    enabled = item.id !in state.busyModelIds,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ai_test_text_${item.id}"),
                ) { Text(testButtonLabel("测试文本", item.textStatus, item.id in state.busyTextTestIds)) }
            }
            if (item.config.supportsVision) {
                Button(
                    onClick = { viewModel.testVision(item.id) },
                    enabled = item.id !in state.busyModelIds,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ai_test_vision_${item.id}"),
                ) { Text(testButtonLabel("测试图片", item.visionStatus, item.id in state.busyVisionTestIds)) }
            }
        }
    }
}

@Composable
private fun AbilityChip(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun FeatureBindingPage(state: AiSettingsUiState, viewModel: AiSettingsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("每项功能只能选择已启用、能力匹配且测试通过的模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(AiFeature.entries) { feature ->
            BindingCard(feature, state, viewModel)
        }
    }
}

@Composable
private fun BindingCard(feature: AiFeature, state: AiSettingsUiState, viewModel: AiSettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.models.firstOrNull { it.id == state.bindings[feature] }
    val options = viewModel.bindingOptions(feature)
    HabitCard(Modifier.fillMaxWidth()) {
        Text(featureLabel(feature), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (feature == AiFeature.WEEKLY_REPORT) "需要文本能力" else "需要图片能力",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("binding_${feature.name}"),
        ) { Text(selected?.name ?: "选择模型") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("暂不绑定") },
                onClick = { viewModel.bind(feature, null); expanded = false },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = { viewModel.bind(feature, option.id); expanded = false },
                )
            }
        }
        if (selected != null && selected !in options) {
            Text("当前绑定需要重新测试或改绑", color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun featureLabel(feature: AiFeature): String = when (feature) {
    AiFeature.WEEKLY_REPORT -> "综合周报"
    AiFeature.MEAL_CALORIE_ESTIMATE -> "图片热量估算"
}

private fun testButtonLabel(base: String, status: AiTestStatus, busy: Boolean): String = when {
    busy -> "测试中…"
    status == AiTestStatus.PASSED -> "$base ✓"
    else -> base
}

private fun formatBeijingTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(HabitTimePolicy.zoneId)
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
