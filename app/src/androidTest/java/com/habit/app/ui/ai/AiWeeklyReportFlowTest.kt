package com.habit.app.ui.ai

import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiPreparedImage
import com.habit.app.data.ai.AiSecretStore
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
import com.habit.app.ui.components.NavigationMode
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiWeeklyReportFlowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun generateConfirmationShowsApprovedPreviewOrderAndExactlyThreeNumberedSuggestionsThenSaves() {
        val fixture = fixture()
        var openedStart: Long? = null
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportScreen(
                    viewModel = fixture.viewModel,
                    navigationMode = NavigationMode.MENU,
                    onNavigation = {},
                    onOpenHistory = {},
                    onOpenSavedReport = { openedStart = it },
                    onOpenModelSettings = {},
                )
            }
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.ReadyToGenerate }

        composeRule.onNodeWithTag("weekly_report_generate").performClick()
        composeRule.onNodeWithText("生成上周综合周报？").assertIsDisplayed()
        assertEquals(0, fixture.client.calls)
        composeRule.onNodeWithTag("weekly_report_generate_confirm").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Preview }

        val orderedTags = listOf(
            "weekly_report_header",
            "weekly_report_metrics",
            "weekly_report_habit",
            "weekly_report_diet",
            "weekly_report_correlation",
            "weekly_report_suggestions",
            "weekly_report_footer",
        )
        val yPositions = orderedTags.map { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
        }
        assertTrue(yPositions.zipWithNext().all { (first, second) -> first < second })
        composeRule.onNodeWithTag("weekly_report_suggestions").performScrollTo()
        composeRule.onAllNodesWithTag("weekly_report_numbered_suggestion").assertCountEquals(3)
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("保持小目标。").assertIsDisplayed()
        composeRule.onNodeWithText("补齐饮食记录。").assertIsDisplayed()
        composeRule.onNodeWithText("继续观察一周。").assertIsDisplayed()

        composeRule.onNodeWithTag("weekly_report_save").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Saved }
        composeRule.runOnIdle { assertNull(openedStart) }
        assertEquals("上周回顾", fixture.reports.reports.value.single().title)
    }

    @Test
    fun historyIsNewestFirstAndNavigationReopensReadOnlyDetailWithBackToRoot() {
        val older = weeklySavedReport(LocalDate.of(2026, 7, 27), "七月底周报", id = 1)
        val newer = weeklySavedReport(LocalDate.of(2026, 8, 3), "八月首周周报", id = 2)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "reports") {
                    composable("reports") {
                        AiWeeklyReportScreen(
                            viewModel = fixture(reports = listOf(newer)).viewModel,
                            navigationMode = NavigationMode.MENU,
                            onNavigation = {},
                            onOpenHistory = { navController.navigate("history") },
                            onOpenSavedReport = { navController.navigate("detail/$it") },
                            onOpenModelSettings = {},
                        )
                    }
                    composable("history") {
                        AiReportHistoryScreen(
                            reports = listOf(older, newer),
                            onBack = { navController.popBackStack() },
                            onOpenReport = { navController.navigate("detail/$it") },
                        )
                    }
                    composable("detail/{start}") { entry ->
                        val start = requireNotNull(entry.arguments?.getString("start")?.toLong())
                        AiWeeklyReportDetailScreen(
                            report = listOf(older, newer).first { it.startEpochDay == start },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("weekly_report_history").performClick()
        val newerTop = composeRule.onNodeWithTag("weekly_history_${newer.startEpochDay}").fetchSemanticsNode().boundsInRoot.top
        val olderTop = composeRule.onNodeWithTag("weekly_history_${older.startEpochDay}").fetchSemanticsNode().boundsInRoot.top
        assertTrue(newerTop < olderTop)
        composeRule.onNodeWithTag("weekly_history_${newer.startEpochDay}").performClick()
        composeRule.onNodeWithText("八月首周周报").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_save").assertDoesNotExist()
        composeRule.onNodeWithTag("weekly_report_generate").assertDoesNotExist()
        composeRule.onNodeWithTag("navigate_back").performClick()
        composeRule.onNodeWithText("历史周报").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate_back").performClick()
        composeRule.onNodeWithText("综合周报").assertIsDisplayed()
    }

    @Test
    fun sameWeekReplacementNeedsConfirmationAndSaveFailureKeepsOldReport() {
        val old = weeklySavedReport(LocalDate.of(2026, 8, 3), "旧周报", id = 8)
        val fixture = fixture(reports = listOf(old), failSave = true)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportScreen(fixture.viewModel, NavigationMode.MENU, {}, {}, {}, {})
            }
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.ReadyToGenerate }
        composeRule.onNodeWithTag("weekly_report_generate").performClick()
        composeRule.onNodeWithTag("weekly_report_generate_confirm").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Preview }

        composeRule.onNodeWithTag("weekly_report_save").performScrollTo().performClick()
        composeRule.onNodeWithText("替换已有周报？").assertIsDisplayed()
        assertEquals("旧周报", fixture.reports.reports.value.single().title)
        composeRule.onNodeWithTag("weekly_report_replace_confirm").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Error }

        composeRule.onNodeWithText("周报保存失败，请重试").assertIsDisplayed()
        assertEquals(listOf(old), fixture.reports.reports.value)
    }

    @Test
    fun confirmedSameWeekReplacementSavesTheNewReport() {
        val old = weeklySavedReport(LocalDate.of(2026, 8, 3), "旧周报", id = 8)
        val fixture = fixture(reports = listOf(old))
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportScreen(fixture.viewModel, NavigationMode.MENU, {}, {}, {}, {})
            }
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.ReadyToGenerate }
        composeRule.onNodeWithTag("weekly_report_generate").performClick()
        composeRule.onNodeWithTag("weekly_report_generate_confirm").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Preview }

        composeRule.onNodeWithTag("weekly_report_save").performScrollTo().performClick()
        composeRule.onNodeWithText("替换已有周报？").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_replace_confirm").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Saved }

        assertEquals(1, fixture.reports.reports.value.size)
        val replaced = fixture.reports.reports.value.single()
        assertEquals(8L, replaced.id)
        assertEquals("上周回顾", replaced.title)
        assertEquals(old.createdAt, replaced.createdAt)
        assertTrue(replaced.updatedAt > old.updatedAt)
        composeRule.onNodeWithText("上周回顾").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_save").assertDoesNotExist()
    }

    @Test
    fun delayedDetailFlowShowsLoadingBeforeFoundWithoutNotFoundFlash() {
        val release = CompletableDeferred<Unit>()
        val delayedReport = flow {
            release.await()
            emit(weeklySavedReport(LocalDate.of(2026, 8, 3), "延迟周报", 3))
        }
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportDetailRoute(
                    reportFlow = delayedReport,
                    onBack = {},
                    onRegenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag("weekly_report_detail_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_detail_not_found").assertDoesNotExist()
        composeRule.runOnIdle { release.complete(Unit) }
        composeRule.onNodeWithText("延迟周报").assertIsDisplayed()
    }

    @Test
    fun missingDetailRouteKeepsStandardBackNavigation() {
        val backCalls = AtomicInteger()
        val missingReport = flowOf<AiWeeklyReport?>(null)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportDetailRoute(missingReport, { backCalls.incrementAndGet() }, {})
            }
        }
        composeRule.onNodeWithTag("weekly_report_detail_not_found").assertIsDisplayed()
        composeRule.onNodeWithTag("navigate_back").performClick()
        assertEquals(1, backCalls.get())
    }

    @Test
    fun busyOverlayHidesUnderlyingSemanticsBlocksBackAndRecoversAfterGenerateAndSave() {
        val client = HangingFlowWeeklyClient()
        val fixture = fixture(client = client, suspendSave = true)
        val menuCalls = AtomicInteger()
        val historyCalls = AtomicInteger()
        val detailCalls = AtomicInteger()
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportScreen(
                    fixture.viewModel,
                    NavigationMode.MENU,
                    { menuCalls.incrementAndGet() },
                    { historyCalls.incrementAndGet() },
                    { detailCalls.incrementAndGet() },
                    {},
                )
            }
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.ReadyToGenerate }
        composeRule.onNodeWithTag("weekly_report_generate").performClick()
        composeRule.onNodeWithTag("weekly_report_generate_confirm").performClick()
        composeRule.waitUntil(5_000) { client.started.isCompleted }

        composeRule.onNodeWithContentDescription("正在生成周报…").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_history").assertDoesNotExist()
        composeRule.onNodeWithTag("open_drawer").assertDoesNotExist()
        composeRule.onNodeWithTag("weekly_report_generate").assertDoesNotExist()
        pressBack()
        assertEquals(0, menuCalls.get() + historyCalls.get() + detailCalls.get())

        composeRule.runOnIdle { client.release.complete(Unit) }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Preview }
        composeRule.onNodeWithTag("weekly_report_history").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_save").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { fixture.reports.saveStarted.isCompleted }

        composeRule.onNodeWithContentDescription("正在保存周报…").assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_history").assertDoesNotExist()
        composeRule.onNodeWithTag("weekly_report_save").assertDoesNotExist()
        pressBack()
        assertEquals(0, menuCalls.get() + historyCalls.get() + detailCalls.get())
        composeRule.runOnIdle { fixture.reports.releaseSave.complete(Unit) }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Saved }
        composeRule.onNodeWithTag("weekly_report_history").performClick()
        assertEquals(1, historyCalls.get())
    }

    @Test
    fun damagedHistorySuggestionsShowIncompleteMessageAndRegenerateEntry() {
        val regenerateCalls = AtomicInteger()
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportDetailScreen(
                    report = weeklySavedReport(LocalDate.of(2026, 8, 3), "损坏周报", 7)
                        .copy(suggestions = listOf("只有一", "只有二")),
                    onBack = {},
                    onRegenerate = { regenerateCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithText("周报建议数据不完整，请重新生成。").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("weekly_report_regenerate").performScrollTo().performClick()
        assertEquals(1, regenerateCalls.get())
        composeRule.onAllNodesWithTag("weekly_report_numbered_suggestion").assertCountEquals(0)
    }

    private fun fixture(
        reports: List<AiWeeklyReport> = emptyList(),
        failSave: Boolean = false,
        client: FlowWeeklyClient = FlowWeeklyClient(),
        suspendSave: Boolean = false,
    ): WeeklyFixture = weeklyTestFixture(reports, failSave, client, suspendSave)
}

internal fun weeklyTestFixture(
    reports: List<AiWeeklyReport> = emptyList(),
    failSave: Boolean = false,
    client: FlowWeeklyClient = FlowWeeklyClient(),
    suspendSave: Boolean = false,
): WeeklyFixture {
    val reportRepository = FlowWeeklyReportRepository(reports, failSave, suspendSave)
    return WeeklyFixture(
        client = client,
        reports = reportRepository,
        viewModel = AiWeeklyReportViewModel(
            inputLoader = WeeklyReportInputLoader { WeeklyReportInputBuildResult.Ready(INPUT) },
            modelRepository = FlowWeeklyModelRepository(),
            reportRepository = reportRepository,
            secretStore = FlowWeeklySecrets,
            client = client,
            dateProvider = FlowWeeklyDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-11T03:00:00Z"), ZoneOffset.UTC),
        ),
    )
}

internal data class WeeklyFixture(
    val client: FlowWeeklyClient,
    val reports: FlowWeeklyReportRepository,
    val viewModel: AiWeeklyReportViewModel,
)

private val INPUT = WeeklyReportInput(
    startEpochDay = LocalDate.of(2026, 8, 3).toEpochDay(),
    endEpochDay = LocalDate.of(2026, 8, 9).toEpochDay(),
    habits = emptyList(),
    dietRecords = emptyList(),
    coverage = WeeklyReportCoverage(7, 3, 2, 2, 1, 1),
    json = "{\"coverage\":\"local\"}",
)

internal fun weeklySavedReport(start: LocalDate, title: String, id: Long) = AiWeeklyReport(
    id = id,
    startEpochDay = start.toEpochDay(),
    endEpochDay = start.plusDays(6).toEpochDay(),
    generatedAt = 1,
    modelNameSnapshot = "测试模型",
    modelIdSnapshot = "test-model",
    title = title,
    overview = "本周有部分记录。",
    habitAnalysis = "计划七次完成三次。",
    dietAnalysis = "记录两天且一条热量缺失。",
    correlationFinding = "覆盖有限，暂不判断关联。",
    suggestions = listOf("保持小目标。", "补齐饮食记录。", "继续观察一周。"),
    cautions = listOf("不是医疗诊断。"),
    coverage = WeeklyReportCoverage(7, 3, 2, 2, 1, 1),
    createdAt = 1,
    updatedAt = 1,
)

internal class FlowWeeklyModelRepository : AiModelRepository {
    private val model = AiModelConfig(
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
        lastTestMessage = "文本能力测试通过",
        createdAt = 1,
        updatedAt = 1,
    )
    override fun observeModels(): Flow<List<AiModelConfig>> = MutableStateFlow(listOf(model))
    override fun observeBindings(): Flow<List<AiFeatureBinding>> = MutableStateFlow(listOf(AiFeatureBinding(AiFeature.WEEKLY_REPORT, 4, 1)))
    override fun observeModel(id: Long): Flow<AiModelConfig?> = MutableStateFlow(model)
    override suspend fun saveModel(id: Long?, draft: AiModelConfigDraft) = error("unused")
    override suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long?) = error("unused")
    override suspend fun bind(feature: AiFeature, modelId: Long?) = error("unused")
    override suspend fun deleteModel(id: Long) = error("unused")
}

internal object FlowWeeklySecrets : AiSecretStore {
    override suspend fun put(externalId: String, apiKey: String) = Unit
    override suspend fun get(externalId: String): String = "sk-secret"
    override suspend fun maskedSuffix(externalId: String): String = "cret"
    override suspend fun remove(externalId: String) = Unit
    override suspend fun clearAll() = Unit
}

internal open class FlowWeeklyClient : AiCompletionClient {
    var calls = 0
    override suspend fun completeText(model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String): String {
        calls++
        return """{"title":"上周回顾","overview":"本周有部分记录。","habitAnalysis":"计划七次完成三次。","dietAnalysis":"记录两天且一条热量缺失。","correlationFinding":"覆盖有限，暂不判断关联。","suggestions":["保持小目标。","补齐饮食记录。","继续观察一周。"],"cautions":["不是医疗诊断。"]}"""
    }
    override suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ) = error("unused")
}

private class HangingFlowWeeklyClient : FlowWeeklyClient() {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    override suspend fun completeText(model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String): String {
        calls++
        started.complete(Unit)
        release.await()
        return super.completeText(model, apiKey, systemPrompt, userPrompt)
    }
}

internal class FlowWeeklyReportRepository(
    initial: List<AiWeeklyReport>,
    private val failSave: Boolean,
    private val suspendSave: Boolean = false,
) : AiWeeklyReportRepository {
    val reports = MutableStateFlow(initial)
    val saveStarted = CompletableDeferred<Unit>()
    val releaseSave = CompletableDeferred<Unit>()
    override fun observeAll(): Flow<List<AiWeeklyReport>> = reports
    override fun observeWeek(startEpochDay: Long): Flow<AiWeeklyReport?> = MutableStateFlow(
        reports.value.firstOrNull { it.startEpochDay == startEpochDay },
    )
    override suspend fun save(report: AiWeeklyReport): Long {
        if (failSave) error("save failed")
        if (suspendSave) {
            saveStarted.complete(Unit)
            releaseSave.await()
        }
        val id = reports.value.firstOrNull { it.startEpochDay == report.startEpochDay }?.id ?: 99
        reports.value = reports.value.filterNot { it.startEpochDay == report.startEpochDay } + report.copy(id = id)
        return id
    }
    override suspend fun delete(id: Long) = error("unused")
}

internal object FlowWeeklyDateProvider : DeviceDateProvider {
    override fun today(): LocalDate = LocalDate.of(2026, 8, 11)
    override val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
}
