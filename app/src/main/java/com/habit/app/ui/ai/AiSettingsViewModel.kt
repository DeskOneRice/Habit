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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val busyModelIds: Set<Long> = emptySet(),
    val deleteImpact: AiDeleteImpact? = null,
    val deletingModelIds: Set<Long> = emptySet(),
    val saving: Boolean = false,
    val recoverableModelId: Long? = null,
    val httpConsentRequested: Boolean = false,
    val message: String? = null,
)

class AiSettingsViewModel internal constructor(
    private val repository: AiModelRepository,
    private val secretStore: AiSecretStore,
    private val client: AiCompletionClient,
    private val clock: Clock = Clock.systemUTC(),
    private val coordinator: AiModelOperationCoordinator = AiModelOperationCoordinator(),
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val keySuffixes = combine(
        repository.observeModels(),
        coordinator.keyRevision,
    ) { models, _ -> models }
        .mapLatest { models ->
            models.associate { model ->
                model.id to runCatching { secretStore.maskedSuffix(model.externalId) }.getOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val activeTab = MutableStateFlow(AiSettingsTab.MODELS)
    private val busyTextTestIds = MutableStateFlow<Set<Long>>(emptySet())
    private val busyVisionTestIds = MutableStateFlow<Set<Long>>(emptySet())
    private val deleteImpact = MutableStateFlow<AiDeleteImpact?>(null)
    private val deletingModelIds = MutableStateFlow<Set<Long>>(emptySet())
    private val saving = MutableStateFlow(false)
    private val recoverableModelId = MutableStateFlow<Long?>(null)
    private val httpConsentRequested = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val saveGate = AtomicBoolean(false)
    private val deleteGate = AtomicBoolean(false)

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<AiSettingsUiState> = combine(
        repository.observeModels(),
        repository.observeBindings(),
        keySuffixes,
        activeTab,
        busyTextTestIds,
        busyVisionTestIds,
        deleteImpact,
        deletingModelIds,
        saving,
        recoverableModelId,
        httpConsentRequested,
        message,
    ) { values ->
        val models = values[0] as List<AiModelConfig>
        val bindings = values[1] as List<AiFeatureBinding>
        val suffixes = values[2] as Map<Long, String?>
        val textBusy = values[4] as Set<Long>
        val visionBusy = values[5] as Set<Long>
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
            busyTextTestIds = textBusy,
            busyVisionTestIds = visionBusy,
            busyModelIds = textBusy + visionBusy,
            deleteImpact = values[6] as AiDeleteImpact?,
            deletingModelIds = values[7] as Set<Long>,
            saving = values[8] as Boolean,
            recoverableModelId = values[9] as Long?,
            httpConsentRequested = values[10] as Boolean,
            message = values[11] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AiSettingsUiState())

    fun selectTab(tab: AiSettingsTab) { activeTab.value = tab }
    fun consumeMessage() { message.value = null }
    fun requestHttpConsent() { httpConsentRequested.value = true }
    fun dismissHttpConsent() { httpConsentRequested.value = false }

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
    ) = if (!saveGate.compareAndSet(false, true)) {
        viewModelScope.launch { }
    } else viewModelScope.launch {
        saving.value = true
        try {
            val validated = validateDraft(draft)
            val targetId = id ?: recoverableModelId.value
            val result = if (targetId == null) {
                saveNewModel(validated, apiKey)
            } else {
                saveExistingModel(targetId, validated, apiKey)
            }
            when (result) {
                is SaveResult.Saved -> {
                    recoverableModelId.value = null
                    message.value = "模型配置已保存"
                    onSaved()
                }
                is SaveResult.Recoverable -> {
                    recoverableModelId.value = result.modelId
                    message.value = RECOVERABLE_KEY_FAILURE_MESSAGE
                }
                SaveResult.Failed -> message.value = SAVE_FAILURE_MESSAGE
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            message.value = failure.message ?: "模型配置不完整"
        } catch (_: Exception) {
            message.value = SAVE_FAILURE_MESSAGE
        } finally {
            saving.value = false
            saveGate.set(false)
        }
    }

    private suspend fun saveNewModel(draft: AiModelConfigDraft, apiKey: String): SaveResult =
        coordinator.withCreate {
            var createdId: Long? = null
            var externalId: String? = null
            try {
                createdId = repository.saveModel(null, draft)
                val saved = repository.observeModel(createdId).first()
                    ?: return@withCreate compensateNewSaveFailure(createdId, externalId)
                externalId = saved.externalId
                val modelMutex = coordinator.register(saved.id, saved.externalId)
                modelMutex.lock()
                try {
                    if (apiKey.isNotBlank()) {
                        secretStore.put(saved.externalId, apiKey.trim())
                        coordinator.invalidateKeys()
                    }
                } finally {
                    modelMutex.unlock()
                }
                SaveResult.Saved(saved.id)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { rollbackCreatedModel(createdId, externalId) }
                throw cancelled
            } catch (_: Exception) {
                compensateNewSaveFailure(createdId, externalId)
            }
        }

    private suspend fun compensateNewSaveFailure(
        createdId: Long?,
        externalId: String?,
    ): SaveResult = withContext(NonCancellable) {
        if (rollbackCreatedModel(createdId, externalId)) {
            SaveResult.Failed
        } else {
            SaveResult.Recoverable(requireNotNull(createdId))
        }
    }

    private suspend fun rollbackCreatedModel(createdId: Long?, externalId: String?): Boolean {
        if (createdId == null) return true
        return try {
            repository.deleteModel(createdId)
            if (externalId != null) {
                try {
                    secretStore.remove(externalId)
                } catch (_: Exception) {
                    // The Room row is already gone; any encrypted orphan is intentionally unreachable.
                } finally {
                    coordinator.invalidateKeys()
                }
            }
            true
        } catch (_: Exception) {
            runCatching {
                repository.recordTest(
                    createdId,
                    AiTestStatus.NEEDS_KEY,
                    encodeTestSnapshot(TestSnapshot(message = "需要重新填写 Key")),
                    null,
                )
            }
            runCatching { clearAllBindingsForModelLocked(createdId) }
            false
        }
    }

    private suspend fun saveExistingModel(
        modelId: Long,
        draft: AiModelConfigDraft,
        apiKey: String,
    ): SaveResult = coordinator.withModel(modelId) {
        val existing = repository.observeModel(modelId).first() ?: return@withModel SaveResult.Failed
        coordinator.register(existing.id, existing.externalId)
        // Read the old secret before mutation. It stays local and is never copied to state or diagnostics.
        try {
            secretStore.get(existing.externalId)
        } catch (_: Exception) {
            return@withModel SaveResult.Failed
        }
        val configurationUnchanged = existing.name == draft.name &&
            existing.baseUrl == draft.baseUrl &&
            existing.modelId == draft.modelId &&
            existing.supportsText == draft.supportsText &&
            existing.supportsVision == draft.supportsVision &&
            existing.allowInsecureHttp == draft.allowInsecureHttp &&
            existing.enabled == draft.enabled &&
            apiKey.isBlank()
        if (configurationUnchanged) return@withModel SaveResult.Saved(existing.id)
        val testInputsChanged = existing.baseUrl != draft.baseUrl ||
            existing.modelId != draft.modelId ||
            existing.supportsText != draft.supportsText ||
            existing.supportsVision != draft.supportsVision ||
            apiKey.isNotBlank()
        val mustResetTests = testInputsChanged || !draft.enabled
        val mustUnbindAll = mustResetTests
        try {
            if (mustResetTests) resetTestsLocked(existing.id)
            if (mustUnbindAll) clearAllBindingsForModelLocked(existing.id)
            val savedId = repository.saveModel(existing.id, draft)
            if (apiKey.isNotBlank()) {
                secretStore.put(existing.externalId, apiKey.trim())
                coordinator.invalidateKeys()
            }
            SaveResult.Saved(savedId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Any partial edit is deliberately left untrusted and unbound.
            runCatching { resetTestsLocked(existing.id) }
            runCatching { clearAllBindingsForModelLocked(existing.id) }
            SaveResult.Failed
        }
    }

    fun testText(modelId: Long) = testCapability(modelId, Capability.TEXT)
    fun testVision(modelId: Long) = testCapability(modelId, Capability.VISION)

    private fun testCapability(modelId: Long, capability: Capability) = viewModelScope.launch {
        val busy = if (capability == Capability.TEXT) busyTextTestIds else busyVisionTestIds
        busy.value = busy.value + modelId
        try {
            coordinator.withModel(modelId) {
                val model = repository.observeModel(modelId).first() ?: return@withModel
                coordinator.register(model.id, model.externalId)
                if (capability == Capability.TEXT && !model.supportsText) {
                    clearCapabilityBindingLocked(model.id, capability)
                    message.value = "当前模型未声明文本能力"
                    return@withModel
                }
                if (capability == Capability.VISION && !model.supportsVision) {
                    clearCapabilityBindingLocked(model.id, capability)
                    message.value = "当前模型未声明图片能力"
                    return@withModel
                }
                val key = try { secretStore.get(model.externalId) } catch (_: Exception) { null }
                if (key.isNullOrBlank()) {
                    recordCapabilityLocked(model, capability, AiTestStatus.NEEDS_KEY, "请先填写 API Key")
                    clearCapabilityBindingLocked(model.id, capability)
                    message.value = "请先填写 API Key"
                    return@withModel
                }
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
                    recordCapabilityLocked(model, capability, AiTestStatus.PASSED, success)
                    message.value = success
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val safe = when (failure) {
                        is AiServiceFailure -> redactAiDiagnostic(failure.userMessage, key)
                        else -> "能力测试失败，请检查配置后重试"
                    }
                    recordCapabilityLocked(model, capability, AiTestStatus.FAILED, safe)
                    clearCapabilityBindingLocked(model.id, capability)
                    message.value = safe
                }
            }
        } finally {
            busy.value = busy.value - modelId
        }
    }

    fun bind(feature: AiFeature, modelId: Long?) = viewModelScope.launch {
        if (modelId == null) {
            coordinator.withBindings { repository.bind(feature, null) }
            message.value = "已取消功能绑定"
            return@launch
        }
        coordinator.withModel(modelId) {
            coordinator.withBindings {
                val model = repository.observeModel(modelId).first()
                val eligible = model?.let { isEligible(it, feature) } == true
                if (!eligible) {
                    message.value = ineligibleMessage(feature)
                    return@withBindings
                }
                repository.bind(feature, modelId)
                message.value = "功能绑定已保存"
            }
        }
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

    fun dismissDelete() { if (!deleteGate.get()) deleteImpact.value = null }

    fun confirmDelete() = deleteImpact.value?.let { impact ->
        if (!deleteGate.compareAndSet(false, true)) return@let viewModelScope.launch { }
        viewModelScope.launch {
        deletingModelIds.value = deletingModelIds.value + impact.modelId
        try {
            coordinator.withModel(impact.modelId) {
                val current = repository.observeModel(impact.modelId).first()
                if (current == null) {
                    deleteImpact.value = null
                    return@withModel
                }
                coordinator.register(current.id, current.externalId)
                try {
                    repository.deleteModel(current.id)
                } catch (_: Exception) {
                    message.value = "删除失败，请重试"
                    return@withModel
                }
                deleteImpact.value = null
                try {
                    secretStore.remove(current.externalId)
                    coordinator.invalidateKeys()
                    message.value = "模型配置已删除，相关功能绑定已清空"
                } catch (_: Exception) {
                    coordinator.invalidateKeys()
                    message.value = DELETE_KEY_CLEANUP_WARNING
                }
            }
        } finally {
            deletingModelIds.value = deletingModelIds.value - impact.modelId
            deleteGate.set(false)
        }
        }
    } ?: viewModelScope.launch { }

    private suspend fun resetTestsLocked(modelId: Long) {
        repository.recordTest(
            modelId,
            AiTestStatus.UNTESTED,
            encodeTestSnapshot(TestSnapshot(message = "尚未验证")),
            null,
        )
    }

    private suspend fun recordCapabilityLocked(
        model: AiModelConfig,
        capability: Capability,
        status: AiTestStatus,
        safeMessage: String,
    ) {
        val current = repository.observeModel(model.id).first() ?: return
        val snapshot = decodeTestSnapshot(current)
        val updated = when (capability) {
            Capability.TEXT -> snapshot.copy(text = status, message = safeMessage)
            Capability.VISION -> snapshot.copy(vision = status, message = safeMessage)
        }
        repository.recordTest(model.id, status, encodeTestSnapshot(updated), clock.millis())
    }

    private suspend fun clearCapabilityBindingLocked(modelId: Long, capability: Capability) {
        val feature = if (capability == Capability.TEXT) AiFeature.WEEKLY_REPORT else AiFeature.MEAL_CALORIE_ESTIMATE
        coordinator.withBindings {
            val current = repository.observeBindings().first().firstOrNull { it.feature == feature }
            if (current?.modelConfigId == modelId) repository.bind(feature, null)
        }
    }

    private suspend fun clearAllBindingsForModelLocked(modelId: Long) {
        coordinator.withBindings {
            repository.observeBindings().first()
                .filter { it.modelConfigId == modelId }
                .forEach { repository.bind(it.feature, null) }
        }
    }

    private fun isEligible(model: AiModelConfig, feature: AiFeature): Boolean {
        if (!model.enabled) return false
        val snapshot = decodeTestSnapshot(model)
        return when (feature) {
            AiFeature.WEEKLY_REPORT -> model.supportsText && snapshot.text == AiTestStatus.PASSED
            AiFeature.MEAL_CALORIE_ESTIMATE -> model.supportsVision && snapshot.vision == AiTestStatus.PASSED
        }
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
    private sealed interface SaveResult {
        data class Saved(val modelId: Long) : SaveResult
        data class Recoverable(val modelId: Long) : SaveResult
        data object Failed : SaveResult
    }
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
        .mapNotNull { item ->
            item.substringBefore('=', "").takeIf(String::isNotBlank)
                ?.let { it to item.substringAfter('=', "") }
        }
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

private fun ineligibleMessage(feature: AiFeature): String = when (feature) {
    AiFeature.WEEKLY_REPORT -> "该模型未通过文本能力测试"
    AiFeature.MEAL_CALORIE_ESTIMATE -> "该模型未通过饮食图片能力测试"
}

private fun neutralTestImage(): AiPreparedImage = AiPreparedImage(
    mimeType = "image/jpeg",
    bytes = Base64.getDecoder().decode(NEUTRAL_JPEG_BASE64),
)

private const val TEST_STATE_PREFIX = "habit-test-v1|"
private const val SAVE_FAILURE_MESSAGE = "模型配置保存失败，请重试"
private const val RECOVERABLE_KEY_FAILURE_MESSAGE = "Key 安全保存失败，已保留未验证配置，请重试"
private const val DELETE_KEY_CLEANUP_WARNING = "模型已删除，但本机 Key 清理失败"
private const val NEUTRAL_JPEG_BASE64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD7dooooA//2Q=="
