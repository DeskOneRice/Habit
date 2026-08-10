package com.habit.app.ui.ai

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAction
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode

@Composable
fun AiModelEditorScreen(
    viewModel: AiSettingsViewModel,
    modelId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing = state.models.firstOrNull { it.id == modelId }
    if (modelId != null && existing == null) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }

    var name by rememberSaveable(modelId) { mutableStateOf(existing?.config?.name.orEmpty()) }
    var baseUrl by rememberSaveable(modelId) { mutableStateOf(existing?.config?.baseUrl.orEmpty()) }
    var requestModelId by rememberSaveable(modelId) { mutableStateOf(existing?.config?.modelId.orEmpty()) }
    // API Key must stay in transient memory and must never enter SavedState, Room, or UiState.
    var apiKey by remember(modelId) { mutableStateOf("") }
    var supportsText by rememberSaveable(modelId) { mutableStateOf(existing?.config?.supportsText ?: true) }
    var supportsVision by rememberSaveable(modelId) { mutableStateOf(existing?.config?.supportsVision ?: false) }
    var allowInsecureHttp by rememberSaveable(modelId) { mutableStateOf(existing?.config?.allowInsecureHttp ?: false) }
    var enabled by rememberSaveable(modelId) { mutableStateOf(existing?.config?.enabled ?: true) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    BackHandler(enabled = state.saving) { }

    fun save() {
        viewModel.saveModel(
            id = modelId,
            draft = AiModelConfigDraft(
                name = name,
                baseUrl = baseUrl,
                modelId = requestModelId,
                supportsText = supportsText,
                supportsVision = supportsVision,
                allowInsecureHttp = allowInsecureHttp,
                enabled = enabled,
            ),
            apiKey = apiKey,
            onSaved = onSaved,
        )
    }

    Scaffold(
        topBar = {
            HabitTopAppBar(
                title = if (modelId == null) "添加模型" else "编辑模型",
                navigationMode = NavigationMode.BACK,
                onNavigation = { if (!state.saving) onBack() },
                actions = {
                    HabitTopAction(
                        text = if (state.saving) "保存中…" else "保存",
                        contentDescription = "保存模型配置",
                        onClick = {
                            if (baseUrl.trim().startsWith("http://") && !allowInsecureHttp) {
                                viewModel.requestHttpConsent()
                            } else {
                                save()
                            }
                        },
                        modifier = Modifier.testTag("ai_model_save"),
                        enabled = !state.saving,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HabitCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("ai_name"),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        if (!it.trim().startsWith("http://")) allowInsecureHttp = false
                    },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("ai_base_url"),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = requestModelId,
                    onValueChange = { requestModelId = it },
                    label = { Text("模型 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("ai_model_id"),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (modelId == null) "API Key" else "替换 API Key（留空则不更换）") },
                    supportingText = existing?.keySuffix?.let { suffix -> { Text("当前 Key：$suffix") } },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("ai_api_key"),
                )
            }

            HabitCard(Modifier.fillMaxWidth()) {
                Text("模型能力", style = MaterialTheme.typography.titleMedium)
                SwitchRow("文本能力", supportsText, { supportsText = it })
                SwitchRow("图片能力", supportsVision, { supportsVision = it })
                SwitchRow("启用此模型", enabled, { enabled = it })
            }

            if (baseUrl.trim().startsWith("http://")) {
                HabitCard(Modifier.fillMaxWidth()) {
                    Text("HTTP 连接风险", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(
                        if (allowInsecureHttp) "已明确允许：网络中的其他设备可能读取 API Key 和数据。"
                        else "HTTP 不会加密 API Key 和数据，保存前必须明确同意。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = viewModel::requestHttpConsent,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (allowInsecureHttp) "重新查看风险" else "查看并确认风险") }
                }
            }
        }
    }

    if (state.httpConsentRequested) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHttpConsent,
            title = { Text("允许 HTTP 连接？") },
            text = { Text("网络中的其他设备可能读取 API Key 和发送给模型的数据。仅在你信任该网络和服务时继续。") },
            confirmButton = {
                Button(
                    onClick = {
                        allowInsecureHttp = true
                        viewModel.dismissHttpConsent()
                    },
                    modifier = Modifier.sizeIn(minHeight = 48.dp).testTag("ai_http_consent_confirm"),
                ) { Text("我已了解并允许") }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissHttpConsent,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
