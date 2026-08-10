package com.habit.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiPreparedImage
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.data.ai.AiServiceFailure
import com.habit.app.data.ai.redactAiDiagnostic
import com.habit.app.domain.ai.normalizedChatCompletionsUrl
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiFeatureBinding
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.repository.AiModelRepository
import java.time.Clock
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AiSettingsTab { MODELS, BINDINGS }

data class AiModelUi(
    val config: AiModelConfig,
    val keySuffix: String?,
    val textStatus: AiTestStatus,
    val visionStatus: AiTestStatus,
    val statusMessage: String,
) {
    val id: Long get() = config.id
    val name: String get() = config.name
}

data class AiDeleteImpact(
    val modelId: Long,
    val modelName: String,
    val features: List<AiFeature>,
)

data class AiSettingsUiState(
    val models: List<AiModelUi> = emptyList(),
    val bindings: Map<AiFeature, Long?> = AiFeature.entries.associateWith { null },
    val activeTab: AiSettingsTab = AiSettingsTab.MODELS,
    val busyTextTestIds: Set<Long> = emptySet(),
    val busyVisionTestIds: Set<Long> = emptySet(),
    val deleteImpact: AiDeleteImpact? = null,
    val httpConsentRequested: Boolean = false,
    val message: String? = null,
)

class AiSettingsViewModel(
    private val repository: AiModelRepository,
    private val secretStore: AiSecretStore,
    private val client: AiCompletionClient,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val keySuffixes = MutableStateFlow<Map<Long, String?>>(emptyMap())
    private val activeTab = MutableStateFlow(AiSettingsTab.MODELS)
    private val busyTextTestIds = MutableStateFlow<Set<Long>>(emptySet())
    private val busyVisionTestIds = MutableStateFlow<Set<Long>>(emptySet())
    private val deleteImpact = MutableStateFlow<AiDeleteImpact?>(null)
    private val httpConsentRequested = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<AiSettingsUiState> = combine(
        repository.observeModels(),
        repository.observeBindings(),
        keySuffixes,
        activeTab,
        busyTextTestIds,
        busyVisionTestIds,
        deleteImpact,
        httpConsentRequested,
        message,
    ) { values ->
        val models = values[0] as List<AiModelConfig>
        val bindings = values[1] as List<AiFeatureBinding>
        val suffixes = values[2] as Map<Long, String?>
        AiSettingsUiState(
            models = models.map { model ->
                val snapshot = decodeTestSnapshot(model)
                AiModelUi(
                    config = model,
                    keySuffix = suffixes[model.id]?.let { "••••$it" },
                    textStatus = snapshot.text,
                    visionStatus = snapshot.vision,
                    statusMessage = snapshot.message,
                )
            },
            bindings = AiFeature.entries.associateWith { feature ->
                bindings.firstOrNull { it.feature == feature }?.modelConfigId
            },
            activeTab = values[3] as AiSettingsTab,
            busyTextTestIds = values[4] as Set<Long>,
            busyVisionTestIds = values[5] as Set<Long>,
            deleteImpact = values[6] as AiDeleteImpact?,
            httpConsentRequested = values[7] as Boolean,
            message = values[8] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AiSettingsUiState())

    init {
        viewModelScope.launch {
            repository.observeModels().collect { models ->
                keySuffixes.value = models.associate { model ->
                    model.id to runCatching { secretStore.maskedSuffix(model.externalId) }.getOrNull()
                }
            }
        }
    }

    fun selectTab(tab: AiSettingsTab) {
        activeTab.value = tab
    }

    fun consumeMessage() {
        message.value = null
    }

    fun requestHttpConsent() {
        httpConsentRequested.value = true
    }

    fun dismissHttpConsent() {
        httpConsentRequested.value = false
    }

    fun saveModel(
        draft: AiModelConfigDraft,
        apiKey: String,
        onSaved: () -> Unit = {},
    ) = saveModel(null, draft, apiKey, onSaved)

    fun saveModel(
        id: Long?,
        draft: AiModelConfigDraft,
        apiKey: String,
        onSaved: () -> Unit = {},
    ) = viewModelScope.launch {
        try {
            val validated = validateDraft(draft)
            val existing = id?.let { repository.observeModel(it).filterNotNull().first() }
            val testInputsChanged = existing != null && (
                existing.baseUrl != validated.baseUrl ||
                    existing.modelId != validated.modelId ||
                    existing.supportsText != validated.supportsText ||
                    existing.supportsVision != validated.supportsVision ||
                    apiKey.isNotBlank()
                )
            // Revoke a prior pass before either persistence layer can fail. Partial saves stay untrusted.
            if (testInputsChanged) {
                repository.recordTest(
                    existing.id,
                    AiTestStatus.UNTESTED,
                    encodeTestSnapshot(TestSnapshot(message = "尚未验证")),
                    null,
                )
            }
            val savedId = repository.saveModel(id, validated)
            val saved = repository.observeModel(savedId).filterNotNull().first()
            if (apiKey.isNotBlank()) secretStore.put(saved.externalId, apiKey.trim())
            refreshKeySuffix(savedId, saved.externalId)
            message.value = "模型配置已保存"
            onSaved()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            message.value = failure.message ?: "模型配置不完整"
        } catch (_: Exception) {
            message.value = "模型配置保存失败，请重试"
        }
    }

    fun testText(modelId: Long) = testCapability(modelId, Capability.TEXT)

    fun testVision(modelId: Long) = testCapability(modelId, Capability.VISION)

    fun bind(feature: AiFeature, modelId: Long?) = viewModelScope.launch {
        if (modelId == null) {
            repository.bind(feature, null)
            message.value = "已取消功能绑定"
            return@launch
        }
        val eligible = bindingOptions(feature).any { it.id == modelId }
        if (!eligible) {
            message.value = when (feature) {
                AiFeature.WEEKLY_REPORT -> "该模型未通过文本能力测试"
                AiFeature.MEAL_CALORIE_ESTIMATE -> "该模型未通过图片能力测试"
            }
            return@launch
        }
        repository.bind(feature, modelId)
        message.value = "功能绑定已保存"
    }

    fun bindingOptions(feature: AiFeature): List<AiModelUi> = state.value.models.filter { item ->
        item.config.enabled && when (feature) {
            AiFeature.WEEKLY_REPORT -> item.config.supportsText && item.textStatus == AiTestStatus.PASSED
            AiFeature.MEAL_CALORIE_ESTIMATE -> item.config.supportsVision && item.visionStatus == AiTestStatus.PASSED
        }
    }

    fun requestDelete(modelId: Long) {
        val model = state.value.models.firstOrNull { it.id == modelId } ?: return
        deleteImpact.value = AiDeleteImpact(
            modelId = modelId,
            modelName = model.name,
            features = state.value.bindings.filterValues { it == modelId }.keys.toList(),
        )
    }

    fun dismissDelete() {
        deleteImpact.value = null
    }

    fun confirmDelete() = viewModelScope.launch {
        val impact = deleteImpact.value ?: return@launch
        val model = repository.observeModel(impact.modelId).filterNotNull().first()
        try {
            secretStore.remove(model.externalId)
            repository.deleteModel(model.id)
            keySuffixes.value = keySuffixes.value - model.id
            deleteImpact.value = null
            message.value = "模型配置已删除，相关功能绑定已清空"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message.value = "删除失败，请重试"
        }
    }

    private fun testCapability(modelId: Long, capability: Capability) = viewModelScope.launch {
        val busy = if (capability == Capability.TEXT) busyTextTestIds else busyVisionTestIds
        val model = repository.observeModel(modelId).filterNotNull().first()
        if (capability == Capability.TEXT && !model.supportsText) {
            message.value = "当前模型未声明文本能力"
            return@launch
        }
        if (capability == Capability.VISION && !model.supportsVision) {
            message.value = "当前模型未声明图片能力"
            return@launch
        }
        val key = try {
            secretStore.get(model.externalId)
        } catch (_: Exception) {
            null
        }
        if (key.isNullOrBlank()) {
            recordCapability(model, capability, AiTestStatus.NEEDS_KEY, "请先填写 API Key")
            message.value = "请先填写 API Key"
            return@launch
        }

        busy.value = busy.value + modelId
        try {
            if (capability == Capability.TEXT) {
                client.completeText(model, key, "你是连接测试助手。", "只回复 OK")
            } else {
                client.completeVision(
                    model,
                    key,
                    "你是图片能力测试助手。",
                    "只回复 OK",
                    listOf(neutralTestImage()),
                )
            }
            val success = if (capability == Capability.TEXT) "文本能力测试通过" else "图片能力测试通过"
            recordCapability(model, capability, AiTestStatus.PASSED, success)
            message.value = success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val safe = when (failure) {
                is AiServiceFailure -> redactAiDiagnostic(failure.userMessage, key)
                else -> "能力测试失败，请检查配置后重试"
            }
            recordCapability(model, capability, AiTestStatus.FAILED, safe)
            message.value = safe
        } finally {
            busy.value = busy.value - modelId
        }
    }

    private suspend fun recordCapability(
        model: AiModelConfig,
        capability: Capability,
        status: AiTestStatus,
        safeMessage: String,
    ) {
        val current = repository.observeModel(model.id).filterNotNull().first()
        val snapshot = decodeTestSnapshot(current)
        val updated = when (capability) {
            Capability.TEXT -> snapshot.copy(text = status, message = safeMessage)
            Capability.VISION -> snapshot.copy(vision = status, message = safeMessage)
        }
        repository.recordTest(model.id, status, encodeTestSnapshot(updated), clock.millis())
    }

    private suspend fun refreshKeySuffix(id: Long, externalId: String) {
        val suffix = runCatching { secretStore.maskedSuffix(externalId) }.getOrNull()
        keySuffixes.value = keySuffixes.value + (id to suffix)
    }

    private fun validateDraft(draft: AiModelConfigDraft): AiModelConfigDraft {
        require(draft.name.isNotBlank()) { "请输入模型名称" }
        require(draft.modelId.isNotBlank()) { "请输入模型 ID" }
        require(draft.supportsText || draft.supportsVision) { "请至少选择一项模型能力" }
        normalizedChatCompletionsUrl(draft.baseUrl, draft.allowInsecureHttp)
        return draft.copy(
            name = draft.name.trim(),
            baseUrl = draft.baseUrl.trim().trimEnd('/'),
            modelId = draft.modelId.trim(),
        )
    }

    private enum class Capability { TEXT, VISION }
}

private data class TestSnapshot(
    val text: AiTestStatus = AiTestStatus.UNTESTED,
    val vision: AiTestStatus = AiTestStatus.UNTESTED,
    val message: String = "",
)

private fun decodeTestSnapshot(model: AiModelConfig): TestSnapshot {
    if (!model.lastTestMessage.startsWith(TEST_STATE_PREFIX)) {
        return TestSnapshot(
            text = if (model.supportsText) model.lastTestStatus else AiTestStatus.UNTESTED,
            vision = AiTestStatus.UNTESTED,
            message = model.lastTestMessage,
        )
    }
    val fields = model.lastTestMessage.removePrefix(TEST_STATE_PREFIX)
        .split('|')
        .mapNotNull { item -> item.substringBefore('=', "").takeIf(String::isNotBlank)?.let { it to item.substringAfter('=', "") } }
        .toMap()
    return TestSnapshot(
        text = fields["text"].toTestStatus(),
        vision = fields["vision"].toTestStatus(),
        message = fields["message"].orEmpty(),
    )
}

private fun encodeTestSnapshot(snapshot: TestSnapshot): String = buildString {
    append(TEST_STATE_PREFIX)
    append("text=").append(snapshot.text.name)
    append("|vision=").append(snapshot.vision.name)
    append("|message=").append(snapshot.message.replace('|', ' '))
}

private fun String?.toTestStatus(): AiTestStatus = runCatching {
    AiTestStatus.valueOf(this.orEmpty())
}.getOrDefault(AiTestStatus.UNTESTED)

private fun neutralTestImage(): AiPreparedImage = AiPreparedImage(
    mimeType = "image/jpeg",
    bytes = Base64.getDecoder().decode(NEUTRAL_JPEG_BASE64),
)

private const val TEST_STATE_PREFIX = "habit-test-v1|"
private const val NEUTRAL_JPEG_BASE64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD7dooooA//2Q=="
