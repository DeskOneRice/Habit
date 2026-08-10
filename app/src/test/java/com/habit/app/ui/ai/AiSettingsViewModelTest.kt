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
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
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
    fun roomEmissionBeforeSecretWriteStillRefreshesMaskedSuffix() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore().apply {
            onPut = { externalId ->
                assertEquals("external-1", externalId)
                assertEquals("模型", repository.models.value.single().name)
            }
        }
        val viewModel = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock)
        advanceUntilIdle()

        viewModel.saveModel(draft(name = "模型"), "sk-after-room-4321")
        advanceUntilIdle()

        assertEquals("••••4321", viewModel.state.value.models.single().keySuffix)
    }

    @Test
    fun visionTestUsesGeneratedNeutralImageAndRejectsTextOnlyBinding() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val client = FakeAiCompletionClient()
        val viewModel = AiSettingsViewModel(repository, secrets, client, clock)
        viewModel.saveModel(draft(name = "纯文本", vision = false), "sk-text")
        advanceUntilIdle()
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
        advanceUntilIdle()
        viewModel.saveModel(draft(name = "未测试视觉", vision = true), "sk-b")
        advanceUntilIdle()
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

    @Test
    fun sameModelTextAndVisionTestsAreSerializedWithoutLosingEitherSnapshot() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val coordinator = AiModelOperationCoordinator()
        val setup = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock, coordinator)
        setup.saveModel(draft(name = "视觉", vision = true), "sk-vision")
        advanceUntilIdle()
        val id = repository.models.value.single().id
        val client = SuspendedAiCompletionClient()
        val viewModel = AiSettingsViewModel(repository, secrets, client, clock, coordinator)

        viewModel.testText(id)
        viewModel.testVision(id)
        dispatcher.scheduler.runCurrent()

        assertTrue(client.textStarted.isCompleted)
        assertFalse(client.visionStarted.isCompleted)
        assertTrue(id in viewModel.state.value.busyModelIds)
        client.releaseText.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertTrue(client.visionStarted.isCompleted)
        client.releaseVision.complete(Unit)
        advanceUntilIdle()

        val result = viewModel.state.value.models.single()
        assertEquals(AiTestStatus.PASSED, result.textStatus)
        assertEquals(AiTestStatus.PASSED, result.visionStatus)
        assertFalse(id in viewModel.state.value.busyModelIds)
    }

    @Test
    fun editFromAnotherViewModelWaitsForTestThenResetsOldResultAndBindings() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val coordinator = AiModelOperationCoordinator()
        val setup = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock, coordinator)
        setup.saveModel(draft(name = "共享", vision = true), "sk-shared")
        advanceUntilIdle()
        val id = repository.models.value.single().id
        setup.testText(id)
        advanceUntilIdle()
        setup.bind(AiFeature.WEEKLY_REPORT, id)
        advanceUntilIdle()

        val client = SuspendedAiCompletionClient()
        val tester = AiSettingsViewModel(repository, secrets, client, clock, coordinator)
        val editor = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock, coordinator)
        tester.testText(id)
        dispatcher.scheduler.runCurrent()
        editor.saveModel(id, draft(name = "共享", url = "https://new.example/v1", vision = true), "")
        dispatcher.scheduler.runCurrent()

        assertEquals("https://api.example/v1", repository.models.value.single().baseUrl)
        client.releaseText.complete(Unit)
        advanceUntilIdle()

        val final = editor.state.value.models.single()
        assertEquals("https://new.example/v1", final.config.baseUrl)
        assertEquals(AiTestStatus.UNTESTED, final.textStatus)
        assertEquals(AiTestStatus.UNTESTED, final.visionStatus)
        assertTrue(repository.bindings.value.all { it.modelConfigId == null })
    }

    @Test
    fun configurationDisableCapabilityRemovalAndFailedTestClearAffectedBindings() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val client = FakeAiCompletionClient()
        val viewModel = AiSettingsViewModel(repository, secrets, client, clock, AiModelOperationCoordinator())
        viewModel.saveModel(draft(name = "共享", vision = true), "sk-shared")
        advanceUntilIdle()
        val id = repository.models.value.single().id
        viewModel.testText(id)
        viewModel.testVision(id)
        advanceUntilIdle()
        viewModel.bind(AiFeature.WEEKLY_REPORT, id)
        viewModel.bind(AiFeature.MEAL_CALORIE_ESTIMATE, id)
        advanceUntilIdle()

        viewModel.saveModel(id, draft(name = "共享", vision = true, enabled = false), "")
        advanceUntilIdle()
        assertTrue(repository.bindings.value.all { it.modelConfigId == null })
        assertEquals(AiTestStatus.UNTESTED, viewModel.state.value.models.single().textStatus)
        assertEquals(AiTestStatus.UNTESTED, viewModel.state.value.models.single().visionStatus)
        assertNull(repository.models.value.single().lastTestedAt)

        viewModel.saveModel(id, draft(name = "共享", vision = true, enabled = true), "")
        viewModel.testText(id)
        viewModel.testVision(id)
        advanceUntilIdle()
        viewModel.bind(AiFeature.WEEKLY_REPORT, id)
        viewModel.bind(AiFeature.MEAL_CALORIE_ESTIMATE, id)
        advanceUntilIdle()
        viewModel.saveModel(id, draft(name = "共享", vision = false), "")
        advanceUntilIdle()
        assertTrue(repository.bindings.value.all { it.modelConfigId == null })

        repository.bind(AiFeature.MEAL_CALORIE_ESTIMATE, id)
        viewModel.testVision(id)
        advanceUntilIdle()
        assertNull(repository.bindings.value.first { it.feature == AiFeature.MEAL_CALORIE_ESTIMATE }.modelConfigId)

        viewModel.testText(id)
        advanceUntilIdle()
        viewModel.bind(AiFeature.WEEKLY_REPORT, id)
        advanceUntilIdle()
        secrets.remove("external-$id")
        viewModel.testText(id)
        advanceUntilIdle()
        assertEquals(AiTestStatus.NEEDS_KEY, viewModel.state.value.models.single().textStatus)
        assertNull(repository.bindings.value.first { it.feature == AiFeature.WEEKLY_REPORT }.modelConfigId)

        secrets.put("external-$id", "sk-restored")
        viewModel.testText(id)
        advanceUntilIdle()
        viewModel.bind(AiFeature.WEEKLY_REPORT, id)
        advanceUntilIdle()
        client.failure = AiServiceFailure(AiFailureKind.AUTH, "API Key 无效或没有模型权限")
        viewModel.testText(id)
        advanceUntilIdle()
        assertNull(repository.bindings.value.first { it.feature == AiFeature.WEEKLY_REPORT }.modelConfigId)
    }

    @Test
    fun bindRevalidatesLatestDisabledModelInsideCoordinator() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val coordinator = AiModelOperationCoordinator()
        val staleBinder = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock, coordinator)
        staleBinder.saveModel(draft(name = "模型"), "sk")
        advanceUntilIdle()
        val id = repository.models.value.single().id
        staleBinder.testText(id)
        advanceUntilIdle()
        assertEquals(listOf(id), staleBinder.bindingOptions(AiFeature.WEEKLY_REPORT).map { it.id })

        val editor = AiSettingsViewModel(repository, secrets, FakeAiCompletionClient(), clock, coordinator)
        editor.saveModel(id, draft(name = "模型", enabled = false), "")
        advanceUntilIdle()
        staleBinder.bind(AiFeature.WEEKLY_REPORT, id)
        advanceUntilIdle()

        assertTrue(repository.bindings.value.none { it.modelConfigId == id })
        assertEquals("该模型未通过文本能力测试", staleBinder.state.value.message)
    }

    @Test
    fun failedNewSecretWriteCompensatesRoomAndFailedCompensationRetriesSameIdOnce() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore().apply { failPut = true }
        val viewModel = AiSettingsViewModel(
            repository, secrets, FakeAiCompletionClient(), clock, AiModelOperationCoordinator(),
        )

        viewModel.saveModel(draft(name = "新模型"), "sk-new")
        advanceUntilIdle()
        assertTrue(repository.models.value.isEmpty())
        assertEquals(1, repository.deleteCalls)
        assertNull(viewModel.state.value.recoverableModelId)

        repository.failDelete = true
        viewModel.saveModel(draft(name = "重试模型"), "sk-new")
        advanceUntilIdle()
        val retainedId = viewModel.state.value.recoverableModelId
        assertNotNull(retainedId)
        assertEquals(AiTestStatus.NEEDS_KEY, repository.models.value.single().lastTestStatus)

        repository.failDelete = false
        secrets.failPut = false
        var savedCallbacks = 0
        viewModel.saveModel(draft(name = "重试模型"), "sk-valid") { savedCallbacks++ }
        viewModel.saveModel(draft(name = "重试模型"), "sk-valid") { savedCallbacks++ }
        advanceUntilIdle()

        assertEquals(1, repository.models.value.size)
        assertEquals(retainedId, repository.models.value.single().id)
        assertEquals(1, savedCallbacks)
        assertEquals(3, repository.saveCalls)
        assertFalse(viewModel.state.value.saving)
    }

    @Test
    fun deleteDatabaseFailureKeepsKeyAndDoubleConfirmDeletesOnlyOnce() = runTest(dispatcher) {
        val repository = FakeAiModelRepository()
        val secrets = FakeAiSecretStore()
        val viewModel = AiSettingsViewModel(
            repository, secrets, FakeAiCompletionClient(), clock, AiModelOperationCoordinator(),
        )
        viewModel.saveModel(draft(name = "删除模型"), "sk-keep")
        advanceUntilIdle()
        val id = repository.models.value.single().id
        repository.failDelete = true
        viewModel.requestDelete(id)
        advanceUntilIdle()
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals("sk-keep", secrets.get("external-1"))
        assertEquals(1, repository.models.value.size)

        repository.failDelete = false
        viewModel.confirmDelete()
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertTrue(repository.models.value.isEmpty())
        assertEquals(2, repository.deleteCalls)
        assertNull(secrets.get("external-1"))
    }

    private fun draft(
        name: String,
        url: String = "https://api.example/v1",
        vision: Boolean = false,
        enabled: Boolean = true,
        text: Boolean = true,
    ) = AiModelConfigDraft(
        name = name,
        baseUrl = url,
        modelId = "gpt-test",
        supportsText = text,
        supportsVision = vision,
        allowInsecureHttp = false,
        enabled = enabled,
    )
}

private class FakeAiModelRepository : AiModelRepository {
    val models = MutableStateFlow<List<AiModelConfig>>(emptyList())
    val bindings = MutableStateFlow<List<AiFeatureBinding>>(emptyList())
    private var nextId = 1L
    var failDelete = false
    var saveCalls = 0
    var deleteCalls = 0

    override fun observeModels(): Flow<List<AiModelConfig>> = models
    override fun observeBindings(): Flow<List<AiFeatureBinding>> = bindings
    override fun observeModel(id: Long): Flow<AiModelConfig?> = MutableStateFlow(models.value.find { it.id == id })

    override suspend fun saveModel(id: Long?, draft: AiModelConfigDraft): Long {
        saveCalls++
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
        deleteCalls++
        if (failDelete) error("database delete failed")
        models.value = models.value.filterNot { it.id == id }
        bindings.value = bindings.value.map { if (it.modelConfigId == id) it.copy(modelConfigId = null) else it }
    }
}

private class FakeAiSecretStore : AiSecretStore {
    private val values = mutableMapOf<String, String>()
    var failPut = false
    var onPut: ((String) -> Unit)? = null
    override suspend fun put(externalId: String, apiKey: String) {
        if (failPut) error("storage failed")
        onPut?.invoke(externalId)
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

private class SuspendedAiCompletionClient : AiCompletionClient {
    val textStarted = CompletableDeferred<Unit>()
    val visionStarted = CompletableDeferred<Unit>()
    val releaseText = CompletableDeferred<Unit>()
    val releaseVision = CompletableDeferred<Unit>()

    override suspend fun completeText(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        textStarted.complete(Unit)
        releaseText.await()
        return "OK"
    }

    override suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ): String {
        visionStarted.complete(Unit)
        releaseVision.await()
        return "OK"
    }
}
