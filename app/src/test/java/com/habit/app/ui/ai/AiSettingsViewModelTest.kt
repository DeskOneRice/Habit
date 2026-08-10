package com.habit.app.ui.ai

import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiPreparedImage
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.data.ai.AiServiceFailure
import com.habit.app.data.ai.AiFailureKind
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiFeatureBinding
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.repository.AiModelRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-08-10T04:05:06Z"), ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun saveTestAndBindTextModelWithoutExposingKey() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val client = FakeAiCompletionClient()
        val viewModel = AiSettingsViewModel(repository, secrets, client, clock)

        viewModel.saveModel(draft(name = "周报模型"), apiKey = "sk-secret-one")
        advanceUntilIdle()

        val saved = repository.models.value.single()
        assertEquals(AiTestStatus.UNTESTED, saved.lastTestStatus)
        assertEquals("••••-one", viewModel.state.value.models.single().keySuffix)
        assertFalse(viewModel.state.value.toString().contains("sk-secret-one"))

        viewModel.testText(saved.id)
        advanceUntilIdle()

        assertEquals(AiTestStatus.PASSED, viewModel.state.value.models.single().textStatus)
        assertEquals("只回复 OK", client.lastTextPrompt)
        assertFalse(repository.models.value.single().lastTestMessage.contains("sk-secret-one"))

        viewModel.bind(AiFeature.WEEKLY_REPORT, saved.id)
        advanceUntilIdle()

        assertEquals(saved.id, repository.bindings.value.single().modelConfigId)
    }

    @Test
    fun visionTestUsesGeneratedNeutralImageAndRejectsTextOnlyBinding() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val client = FakeAiCompletionClient()
        val viewModel = AiSettingsViewModel(repository, secrets, client, clock)
        viewModel.saveModel(draft(name = "纯文本", vision = false), "sk-text")
        viewModel.saveModel(draft(name = "视觉", vision = true), "sk-vision")
        advanceUntilIdle()
        val textOnly = repository.models.value.first { it.name == "纯文本" }
        val vision = repository.models.value.first { it.name == "视觉" }

        viewModel.bind(AiFeature.MEAL_CALORIE_ESTIMATE, textOnly.id)
        advanceUntilIdle()
        assertTrue(repository.bindings.value.isEmpty())
        assertEquals("该模型未通过图片能力测试", viewModel.state.value.message)

        viewModel.testVision(vision.id)
        advanceUntilIdle()
        assertEquals(AiTestStatus.PASSED, viewModel.state.value.models.first { it.id == vision.id }.visionStatus)
        assertEquals("image/jpeg", client.lastVisionImage?.mimeType)
        assertTrue(client.lastVisionImage?.bytes?.isNotEmpty() == true)

        viewModel.bind(AiFeature.MEAL_CALORIE_ESTIMATE, vision.id)
        advanceUntilIdle()
        assertEquals(vision.id, repository.bindings.value.single().modelConfigId)
    }

    @Test
    fun endpointModelCapabilityOrKeyChangeResetsTests() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val viewModel = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock)
        viewModel.saveModel(draft(name = "模型", vision = true), "sk-old")
        advanceUntilIdle()
        val model = repository.models.value.single()
        viewModel.testText(model.id)
        viewModel.testVision(model.id)
        advanceUntilIdle()
        assertEquals(AiTestStatus.PASSED, viewModel.state.value.models.single().textStatus)
        assertEquals(AiTestStatus.PASSED, viewModel.state.value.models.single().visionStatus)

        viewModel.saveModel(model.id, draft(name = "改地址", url = "https://other.example/v1", vision = true), "")
        advanceUntilIdle()
        assertEquals(AiTestStatus.UNTESTED, viewModel.state.value.models.single().textStatus)
        assertEquals(AiTestStatus.UNTESTED, viewModel.state.value.models.single().visionStatus)
        assertNull(repository.models.value.single().lastTestedAt)

        viewModel.testText(model.id)
        advanceUntilIdle()
        viewModel.saveModel(model.id, draft(name = "改能力", vision = false), "")
        advanceUntilIdle()
        assertEquals(AiTestStatus.UNTESTED, viewModel.state.value.models.single().textStatus)

        viewModel.testText(model.id)
        advanceUntilIdle()
        viewModel.saveModel(model.id, draft(name = "换 Key", vision = false), "sk-new")
        advanceUntilIdle()
        assertEquals(AiTestStatus.UNTESTED, viewModel.state.value.models.single().textStatus)
    }

    @Test
    fun bindingOptionsRequireEnabledCapabilityAndCorrespondingPassedTest() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val client = FakeAiCompletionClient()
        val viewModel = AiSettingsViewModel(repository, secrets, client, clock)
        viewModel.saveModel(draft(name = "可用视觉", vision = true), "sk-a")
        viewModel.saveModel(draft(name = "未测试视觉", vision = true), "sk-b")
        viewModel.saveModel(draft(name = "停用文本", enabled = false), "sk-c")
        advanceUntilIdle()
        val tested = repository.models.value.first { it.name == "可用视觉" }
        viewModel.testText(tested.id)
        viewModel.testVision(tested.id)
        advanceUntilIdle()

        assertEquals(listOf(tested.id), viewModel.bindingOptions(AiFeature.WEEKLY_REPORT).map { it.id })
        assertEquals(listOf(tested.id), viewModel.bindingOptions(AiFeature.MEAL_CALORIE_ESTIMATE).map { it.id })
    }

    @Test
    fun deleteShowsBindingImpactThenRemovesSecretAndLeavesBindingsNull() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val viewModel = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock)
        viewModel.saveModel(draft(name = "共享模型", vision = true), "sk-delete")
        advanceUntilIdle()
        val id = repository.models.value.single().id
        viewModel.testText(id)
        viewModel.testVision(id)
        advanceUntilIdle()
        viewModel.bind(AiFeature.WEEKLY_REPORT, id)
        viewModel.bind(AiFeature.MEAL_CALORIE_ESTIMATE, id)
        advanceUntilIdle()

        viewModel.requestDelete(id)
        advanceUntilIdle()
        assertEquals(
            setOf(AiFeature.WEEKLY_REPORT, AiFeature.MEAL_CALORIE_ESTIMATE),
            viewModel.state.value.deleteImpact?.features?.toSet(),
        )
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertTrue(repository.models.value.isEmpty())
        assertNull(secrets.get("external-1"))
        assertTrue(repository.bindings.value.all { it.modelConfigId == null })
    }

    @Test
    fun serviceFailurePersistsOnlySanitizedChineseMessage() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val client = FakeAiCompletionClient().apply {
            failure = AiServiceFailure(
                AiFailureKind.AUTH,
                "API Key 无效或没有模型权限",
                "Authorization: Bearer sk-never-store",
            )
        }
        val viewModel = AiSettingsViewModel(repository, secrets, client, clock)
        viewModel.saveModel(draft(name = "失败模型"), "sk-never-store")
        advanceUntilIdle()

        viewModel.testText(repository.models.value.single().id)
        advanceUntilIdle()

        val saved = repository.models.value.single()
        assertEquals(AiTestStatus.FAILED, saved.lastTestStatus)
        assertEquals("API Key 无效或没有模型权限", viewModel.state.value.message)
        assertFalse(saved.lastTestMessage.contains("sk-never-store"))
        assertFalse(saved.lastTestMessage.contains("Authorization", ignoreCase = true))
    }

    @Test
    fun keyStorageFailureRevokesPreviouslyPassedStatusBeforeReturningError() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val viewModel = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock)
        viewModel.saveModel(draft(name = "模型"), "sk-old")
        advanceUntilIdle()
        val model = repository.models.value.single()
        viewModel.testText(model.id)
        advanceUntilIdle()
        assertEquals(AiTestStatus.PASSED, viewModel.state.value.models.single().textStatus)

        secrets.failPut = true
        viewModel.saveModel(model.id, draft(name = "模型"), "sk-new")
        advanceUntilIdle()

        assertEquals(AiTestStatus.UNTESTED, viewModel.state.value.models.single().textStatus)
        assertEquals("模型配置保存失败，请重试", viewModel.state.value.message)
    }

    private fun draft(
        name: String,
        url: String = "https://api.example/v1",
        vision: Boolean = false,
        enabled: Boolean = true,
    ) = AiModelConfigDraft(
        name = name,
        baseUrl = url,
        modelId = "gpt-test",
        supportsText = true,
        supportsVision = vision,
        allowInsecureHttp = false,
        enabled = enabled,
    )
}

private class FakeAiModelRepository : AiModelRepository {
    val models = MutableStateFlow<List<AiModelConfig>>(emptyList())
    val bindings = MutableStateFlow<List<AiFeatureBinding>>(emptyList())
    private var nextId = 1L

    override fun observeModels(): Flow<List<AiModelConfig>> = models
    override fun observeBindings(): Flow<List<AiFeatureBinding>> = bindings
    override fun observeModel(id: Long): Flow<AiModelConfig?> = MutableStateFlow(models.value.find { it.id == id })

    override suspend fun saveModel(id: Long?, draft: AiModelConfigDraft): Long {
        val existing = id?.let { modelId -> models.value.first { it.id == modelId } }
        val savedId = existing?.id ?: nextId++
        val now = 1L
        val saved = AiModelConfig(
            id = savedId,
            externalId = existing?.externalId ?: "external-$savedId",
            name = draft.name,
            baseUrl = draft.baseUrl,
            modelId = draft.modelId,
            supportsText = draft.supportsText,
            supportsVision = draft.supportsVision,
            allowInsecureHttp = draft.allowInsecureHttp,
            enabled = draft.enabled,
            lastTestedAt = existing?.lastTestedAt,
            lastTestStatus = existing?.lastTestStatus ?: AiTestStatus.UNTESTED,
            lastTestMessage = existing?.lastTestMessage.orEmpty(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        models.value = models.value.filterNot { it.id == savedId } + saved
        return savedId
    }

    override suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long?) {
        models.value = models.value.map { model ->
            if (model.id == id) model.copy(lastTestStatus = status, lastTestMessage = message, lastTestedAt = testedAt)
            else model
        }
    }

    override suspend fun bind(feature: AiFeature, modelId: Long?) {
        bindings.value = bindings.value.filterNot { it.feature == feature } + AiFeatureBinding(feature, modelId, 1)
    }

    override suspend fun deleteModel(id: Long) {
        models.value = models.value.filterNot { it.id == id }
        bindings.value = bindings.value.map { if (it.modelConfigId == id) it.copy(modelConfigId = null) else it }
    }
}

private class FakeAiSecretStore : AiSecretStore {
    private val values = mutableMapOf<String, String>()
    var failPut = false
    override suspend fun put(externalId: String, apiKey: String) {
        if (failPut) error("storage failed")
        values[externalId] = apiKey
    }
    override suspend fun get(externalId: String): String? = values[externalId]
    override suspend fun maskedSuffix(externalId: String): String? = values[externalId]?.takeLast(4)
    override suspend fun remove(externalId: String) { values.remove(externalId) }
    override suspend fun clearAll() { values.clear() }
}

private class FakeAiCompletionClient : AiCompletionClient {
    var failure: AiServiceFailure? = null
    var lastTextPrompt: String? = null
    var lastVisionImage: AiPreparedImage? = null

    override suspend fun completeText(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        failure?.let { throw it }
        lastTextPrompt = userPrompt
        return "OK"
    }

    override suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ): String {
        failure?.let { throw it }
        lastVisionImage = images.single()
        return "OK"
    }
}
