package com.habit.app.ui.ai

import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiPreparedImage
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.data.ai.AiServiceFailure
import com.habit.app.data.ai.AiFailureKind
import com.habit.app.domain.ai.WeeklyReportInput
import com.habit.app.domain.ai.WeeklyReportInputBuildResult
import com.habit.app.domain.ai.WeeklyReportInputLoader
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiFeatureBinding
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.model.WeeklyReportCoverage
import com.habit.app.domain.repository.AiModelRepository
import com.habit.app.domain.repository.AiWeeklyReportRepository
import com.habit.app.domain.time.DeviceDateProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiWeeklyReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-08-11T03:00:00Z"), ZoneOffset.UTC)
    private val input = WeeklyReportInput(
        startEpochDay = LocalDate.of(2026, 8, 3).toEpochDay(),
        endEpochDay = LocalDate.of(2026, 8, 9).toEpochDay(),
        habits = emptyList(),
        dietRecords = emptyList(),
        coverage = WeeklyReportCoverage(7, 3, 2, 2, 1, 1),
        json = "{\"coverage\":\"local\"}",
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun noLocalDataIsExplicitAndRecoverableWithoutCallingAi() = runTest(dispatcher) {
        val client = FakeWeeklyClient()
        val viewModel = viewModel(
            loader = WeeklyReportInputLoader { WeeklyReportInputBuildResult.NoAnalyzableData },
            client = client,
        )
        advanceUntilIdle()

        val error = viewModel.state.value as AiWeeklyReportState.Error
        assertSame(AiWeeklyReportFailure.NoAnalyzableData, error.failure)
        assertEquals(0, client.calls)

        viewModel.retry()
        advanceUntilIdle()
        assertSame(AiWeeklyReportFailure.NoAnalyzableData, (viewModel.state.value as AiWeeklyReportState.Error).failure)
    }

    @Test
    fun generationRechecksLatestBindingModelTestAndDecryptableKey() = runTest(dispatcher) {
        val models = FakeWeeklyModelRepository(validModel())
        val secrets = FakeWeeklySecrets("sk-secret")
        val client = FakeWeeklyClient()
        val viewModel = viewModel(models = models, secrets = secrets, client = client)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is AiWeeklyReportState.ReadyToGenerate)

        models.model.value = models.model.value?.copy(enabled = false)
        viewModel.generate()
        advanceUntilIdle()
        assertSame(AiWeeklyReportFailure.ModelUnavailable, (viewModel.state.value as AiWeeklyReportState.Error).failure)
        assertEquals(0, client.calls)

        models.model.value = validModel(lastTestMessage = "habit-test-v1|text=FAILED|vision=PASSED|message=图片通过")
        viewModel.retry()
        advanceUntilIdle()
        viewModel.generate()
        advanceUntilIdle()
        assertSame(AiWeeklyReportFailure.ModelUnavailable, (viewModel.state.value as AiWeeklyReportState.Error).failure)
        assertEquals(0, client.calls)

        models.model.value = validModel(lastTestMessage = "habit-test-v1|text=PASSED|vision=UNTESTED|message=文本通过")
        secrets.value = null
        viewModel.retry()
        advanceUntilIdle()
        viewModel.generate()
        advanceUntilIdle()
        assertSame(AiWeeklyReportFailure.KeyUnavailable, (viewModel.state.value as AiWeeklyReportState.Error).failure)
        assertEquals(0, client.calls)
        assertFalse(viewModel.state.value.toString().contains("sk-secret"))
    }

    @Test
    fun hangingGenerationIsSingleFlightCancelableAndSerializesConfigurationMutation() = runTest(dispatcher) {
        val models = FakeWeeklyModelRepository(validModel())
        val coordinator = AiModelOperationCoordinator()
        val client = HangingWeeklyClient()
        val viewModel = viewModel(models = models, client = client, coordinator = coordinator)
        advanceUntilIdle()

        viewModel.generate()
        viewModel.generate()
        runCurrent()
        client.started.await()
        assertEquals(1, client.calls)
        assertTrue(viewModel.state.value is AiWeeklyReportState.Generating)

        val editFinished = CompletableDeferred<Unit>()
        launch {
            coordinator.withModel(4, "external") {
                models.model.value = models.model.value?.copy(modelId = "changed")
                editFinished.complete(Unit)
            }
        }
        runCurrent()
        assertFalse(editFinished.isCompleted)

        viewModel.cancelGeneration()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is AiWeeklyReportState.ReadyToGenerate)
        assertTrue(editFinished.isCompleted)
        assertEquals("changed", models.model.value?.modelId)
        assertFalse(viewModel.state.value.toString().contains("raw-secret-response"))
    }

    @Test
    fun immediateCancelBeforeFirstDispatchRestoresReadyAndReleasesGateForGenerateAndSave() = runTest(dispatcher) {
        val reports = FakeWeeklyReportRepository()
        val client = FakeWeeklyClient()
        val viewModel = viewModel(reports = reports, client = client)
        advanceUntilIdle()

        viewModel.generate()
        assertTrue(viewModel.state.value is AiWeeklyReportState.Generating)
        viewModel.cancelGeneration()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is AiWeeklyReportState.ReadyToGenerate)
        assertEquals(0, client.calls)
        viewModel.generate()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is AiWeeklyReportState.Preview)
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is AiWeeklyReportState.Saved)
        assertEquals(1, reports.saveCalls)
    }

    @Test
    fun scopeChildCancellationBeforeFirstDispatchDoesNotLeakStateOrGate() = runTest(dispatcher) {
        val client = FakeWeeklyClient()
        val viewModel = viewModel(client = client)
        advanceUntilIdle()

        val scopeChild = viewModel.generate()
        scopeChild.cancel(CancellationException("viewModel scope cancelled"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value is AiWeeklyReportState.ReadyToGenerate)
        viewModel.generate()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is AiWeeklyReportState.Preview)
        assertEquals(1, client.calls)
    }

    @Test
    fun completionReleasesGateBeforePublishingActionableStateToSynchronousCollector() = runTest(dispatcher) {
        val client = FakeWeeklyClient()
        val viewModel = viewModel(client = client)
        advanceUntilIdle()
        viewModel.generate()
        val followUp = launch(
            UnconfinedTestDispatcher(testScheduler),
            start = CoroutineStart.UNDISPATCHED,
        ) {
            viewModel.state.drop(1).first()
            viewModel.generate()
        }

        viewModel.cancelGeneration()
        advanceUntilIdle()

        followUp.join()
        assertEquals(1, client.calls)
        assertTrue(viewModel.state.value is AiWeeklyReportState.Preview)
    }

    @Test
    fun validResponseCreatesPreviewWithImmutableLocalCoverageAndErrorsNeverExposeRawOrKey() = runTest(dispatcher) {
        val client = FakeWeeklyClient()
        val viewModel = viewModel(client = client)
        advanceUntilIdle()
        viewModel.generate()
        advanceUntilIdle()

        val preview = viewModel.state.value as AiWeeklyReportState.Preview
        assertEquals(input.coverage, preview.draft.coverage)
        assertEquals(3, preview.draft.suggestions.size)
        assertEquals(input.json, client.userPrompt?.substringAfterLast("\n"))
        assertFalse(preview.toString().contains(client.response))
        assertFalse(preview.toString().contains("sk-secret"))

        client.failure = AiServiceFailure(
            AiFailureKind.SERVER,
            "Bearer sk-secret raw-secret-response",
        )
        viewModel.generate()
        advanceUntilIdle()
        val error = viewModel.state.value as AiWeeklyReportState.Error
        assertFalse(error.message.contains("sk-secret"))
        assertFalse(error.message.contains("raw-secret-response"))
        assertTrue(error.recoverTo is AiWeeklyReportState.Preview)
    }

    @Test
    fun unsupportedTextCompletionUsesCapabilityNeutralMessage() = runTest(dispatcher) {
        val client = FakeWeeklyClient().apply {
            failure = AiServiceFailure(AiFailureKind.UNSUPPORTED, "provider raw")
        }
        val viewModel = viewModel(client = client)
        advanceUntilIdle()
        viewModel.generate()
        advanceUntilIdle()

        val error = viewModel.state.value as AiWeeklyReportState.Error
        assertEquals("当前模型不支持此分析请求", error.message)
        assertFalse(error.message.contains("图片"))
    }

    @Test
    fun existingWeekRequiresReplacementEventAndDuplicateSaveOrConfirmUpsertsOnce() = runTest(dispatcher) {
        val reports = FakeWeeklyReportRepository(existingReport())
        val viewModel = viewModel(reports = reports)
        advanceUntilIdle()
        viewModel.generate()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is AiWeeklyReportState.Preview)

        viewModel.save()
        runCurrent()
        assertEquals(0, reports.saveCalls)
        assertEquals(input.startEpochDay, viewModel.replacementRequests.value?.startEpochDay)

        viewModel.confirmReplacement()
        viewModel.confirmReplacement()
        advanceUntilIdle()
        assertEquals(1, reports.saveCalls)
        assertTrue(viewModel.state.value is AiWeeklyReportState.Saved)

        viewModel.save()
        advanceUntilIdle()
        assertEquals(1, reports.saveCalls)
    }

    @Test
    fun savedStateUsesRepositoryRoundTripTimestampsAndReplacementPreservesCreatedAt() = runTest(dispatcher) {
        val existing = existingReport().copy(createdAt = 41, updatedAt = 42)
        val reports = TickingWeeklyReportRepository(existing, firstTick = 9_000)
        val viewModel = viewModel(reports = reports)
        advanceUntilIdle()
        viewModel.generate()
        advanceUntilIdle()

        viewModel.save()
        runCurrent()
        viewModel.confirmReplacement()
        advanceUntilIdle()

        val saved = (viewModel.state.value as AiWeeklyReportState.Saved).report
        assertEquals(reports.current, saved)
        assertEquals(41L, saved.createdAt)
        assertEquals(9_000L, saved.updatedAt)
    }

    @Test
    fun saveLookupSynchronouslyExcludesRegenerationOfAnotherDraft() = runTest(dispatcher) {
        val reports = FakeWeeklyReportRepository()
        val client = FakeWeeklyClient()
        val viewModel = viewModel(reports = reports, client = client)
        advanceUntilIdle()
        viewModel.generate()
        advanceUntilIdle()
        assertEquals(1, client.calls)

        reports.blockNextObserve = CompletableDeferred()
        viewModel.save()
        runCurrent()
        reports.blockedObserveStarted.await()
        viewModel.generate()
        runCurrent()

        assertEquals(1, client.calls)
        reports.blockNextObserve?.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is AiWeeklyReportState.Saved)
    }

    @Test
    fun applicationScopedWeekLockPreventsTwoViewModelsFromReplacingWithoutConfirmation() = runTest(dispatcher) {
        val reports = HangingFirstSaveReportRepository()
        val coordinator = AiModelOperationCoordinator()
        val first = viewModel(reports = reports, coordinator = coordinator)
        val second = viewModel(reports = reports, coordinator = coordinator)
        advanceUntilIdle()
        first.generate()
        second.generate()
        advanceUntilIdle()

        first.save()
        runCurrent()
        reports.firstSaveStarted.await()
        second.save()
        runCurrent()

        assertEquals(1, reports.saveCalls)
        assertNull(second.replacementRequests.value)
        reports.releaseFirstSave.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, reports.saveCalls)
        assertEquals(input.startEpochDay, second.replacementRequests.value?.startEpochDay)
        assertTrue(second.state.value is AiWeeklyReportState.Preview)
    }

    private fun viewModel(
        loader: WeeklyReportInputLoader = WeeklyReportInputLoader { WeeklyReportInputBuildResult.Ready(input) },
        models: FakeWeeklyModelRepository = FakeWeeklyModelRepository(validModel()),
        reports: AiWeeklyReportRepository = FakeWeeklyReportRepository(),
        secrets: FakeWeeklySecrets = FakeWeeklySecrets("sk-secret"),
        client: AiCompletionClient = FakeWeeklyClient(),
        coordinator: AiModelOperationCoordinator = AiModelOperationCoordinator(),
    ) = AiWeeklyReportViewModel(
        inputLoader = loader,
        modelRepository = models,
        reportRepository = reports,
        secretStore = secrets,
        client = client,
        dateProvider = FixedDateProvider,
        clock = clock,
        coordinator = coordinator,
    )

    private fun validModel(lastTestMessage: String = "文本能力测试通过") = AiModelConfig(
        id = 4,
        externalId = "external",
        name = "周报模型",
        baseUrl = "https://example.test/v1",
        modelId = "text-model",
        supportsText = true,
        supportsVision = false,
        allowInsecureHttp = false,
        enabled = true,
        lastTestedAt = 1,
        lastTestStatus = AiTestStatus.PASSED,
        lastTestMessage = lastTestMessage,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun existingReport() = AiWeeklyReport(
        id = 9,
        startEpochDay = input.startEpochDay,
        endEpochDay = input.endEpochDay,
        generatedAt = 2,
        modelNameSnapshot = "旧模型",
        modelIdSnapshot = "old",
        title = "旧周报",
        overview = "旧概览",
        habitAnalysis = "旧习惯",
        dietAnalysis = "旧饮食",
        correlationFinding = "旧观察",
        suggestions = listOf("旧一", "旧二", "旧三"),
        cautions = listOf("旧提示"),
        coverage = input.coverage,
        createdAt = 2,
        updatedAt = 2,
    )
}

private object FixedDateProvider : DeviceDateProvider {
    override fun today(): LocalDate = LocalDate.of(2026, 8, 11)
    override val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
}

private class FakeWeeklyModelRepository(initial: AiModelConfig?) : AiModelRepository {
    val model = MutableStateFlow(initial)
    val binding = MutableStateFlow(listOf(AiFeatureBinding(AiFeature.WEEKLY_REPORT, initial?.id, 1)))
    override fun observeModels(): Flow<List<AiModelConfig>> = MutableStateFlow(listOfNotNull(model.value))
    override fun observeBindings(): Flow<List<AiFeatureBinding>> = binding
    override fun observeModel(id: Long): Flow<AiModelConfig?> = model
    override suspend fun saveModel(id: Long?, draft: AiModelConfigDraft) = error("unused")
    override suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long?) = error("unused")
    override suspend fun bind(feature: AiFeature, modelId: Long?) {
        binding.value = listOf(AiFeatureBinding(feature, modelId, 2))
    }
    override suspend fun deleteModel(id: Long) = error("unused")
}

private class FakeWeeklyReportRepository(initial: AiWeeklyReport? = null) : AiWeeklyReportRepository {
    private val report = MutableStateFlow(initial)
    var saveCalls = 0
    var blockNextObserve: CompletableDeferred<Unit>? = null
    val blockedObserveStarted = CompletableDeferred<Unit>()
    override fun observeAll(): Flow<List<AiWeeklyReport>> = MutableStateFlow(listOfNotNull(report.value))
    override fun observeWeek(startEpochDay: Long): Flow<AiWeeklyReport?> = kotlinx.coroutines.flow.flow {
        blockNextObserve?.let { gate ->
            blockedObserveStarted.complete(Unit)
            gate.await()
            blockNextObserve = null
        }
        emit(report.value)
    }
    override suspend fun save(report: AiWeeklyReport): Long {
        saveCalls++
        this.report.value = report.copy(id = this.report.value?.id ?: 10)
        return this.report.value!!.id
    }
    override suspend fun delete(id: Long) = error("unused")
}

private class HangingFirstSaveReportRepository : AiWeeklyReportRepository {
    private val report = MutableStateFlow<AiWeeklyReport?>(null)
    var saveCalls = 0
    val firstSaveStarted = CompletableDeferred<Unit>()
    val releaseFirstSave = CompletableDeferred<Unit>()

    override fun observeAll(): Flow<List<AiWeeklyReport>> = MutableStateFlow(emptyList())
    override fun observeWeek(startEpochDay: Long): Flow<AiWeeklyReport?> = report
    override suspend fun save(report: AiWeeklyReport): Long {
        saveCalls++
        if (saveCalls == 1) {
            firstSaveStarted.complete(Unit)
            releaseFirstSave.await()
        }
        this.report.value = report.copy(id = 10)
        return 10
    }
    override suspend fun delete(id: Long) = error("unused")
}

private class TickingWeeklyReportRepository(
    initial: AiWeeklyReport?,
    firstTick: Long,
) : AiWeeklyReportRepository {
    private val report = MutableStateFlow(initial)
    private var nextTick = firstTick
    val current: AiWeeklyReport get() = requireNotNull(report.value)

    override fun observeAll(): Flow<List<AiWeeklyReport>> = MutableStateFlow(listOfNotNull(report.value))
    override fun observeWeek(startEpochDay: Long): Flow<AiWeeklyReport?> = report
    override suspend fun save(report: AiWeeklyReport): Long {
        val existing = this.report.value
        val persisted = report.copy(
            id = existing?.id ?: 10,
            createdAt = existing?.createdAt ?: nextTick++,
            updatedAt = nextTick++,
        )
        this.report.value = persisted
        return persisted.id
    }
    override suspend fun delete(id: Long) = error("unused")
}

private class FakeWeeklySecrets(var value: String?) : AiSecretStore {
    override suspend fun put(externalId: String, apiKey: String) { value = apiKey }
    override suspend fun get(externalId: String): String? = value
    override suspend fun maskedSuffix(externalId: String): String? = value?.takeLast(4)
    override suspend fun remove(externalId: String) { value = null }
    override suspend fun clearAll() { value = null }
}

private open class FakeWeeklyClient : AiCompletionClient {
    var calls = 0
    var failure: AiServiceFailure? = null
    var userPrompt: String? = null
    val response = """
        {"title":"上周回顾","overview":"本周有部分记录。","habitAnalysis":"计划七次完成三次。","dietAnalysis":"记录两天且一条热量缺失。","correlationFinding":"覆盖有限，暂不判断关联。","suggestions":["保持小目标。","补齐饮食记录。","继续观察一周。"],"cautions":["不是医疗诊断。"]}
    """.trimIndent()
    override suspend fun completeText(model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String): String {
        calls++
        this.userPrompt = userPrompt
        failure?.let { throw it }
        return response
    }
    override suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ) = error("unused")
}

private class HangingWeeklyClient : FakeWeeklyClient() {
    val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    override suspend fun completeText(model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String): String {
        calls++
        started.complete(Unit)
        try {
            release.await()
        } finally {
            // Cancellation is the behavior under test; no raw response is retained.
        }
        return "raw-secret-response"
    }
}
