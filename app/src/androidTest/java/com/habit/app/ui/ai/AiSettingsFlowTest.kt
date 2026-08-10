package com.habit.app.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiPreparedImage
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiFeatureBinding
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.repository.AiModelRepository
import com.habit.app.ui.navigation.HabitDestination
import com.habit.app.ui.navigation.HabitDrawerContent
import com.habit.app.ui.navigation.navigateFromDrawer
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class AiSettingsFlowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun editorAddsTwoModelsAndReturnsToMaskedModelCards() {
        val fixture = fixture()
        val savedCount = mutableIntStateOf(0)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                key(savedCount.intValue) {
                    if (savedCount.intValue < 2) {
                        AiModelEditorScreen(
                            viewModel = fixture.viewModel,
                            modelId = null,
                            onBack = {},
                            onSaved = { savedCount.intValue++ },
                        )
                    } else {
                        AiSettingsScreen(fixture.viewModel, {}, {}, {})
                    }
                }
            }
        }

        fillEditor("周报模型", "gpt-week", "sk-week-1234")
        composeRule.onNodeWithTag("ai_model_save").performClick()
        composeRule.waitUntil(5_000) { savedCount.intValue == 1 }
        fillEditor("图片模型", "gpt-image", "sk-image-5678")
        composeRule.onNodeWithTag("ai_model_save").performClick()
        composeRule.waitUntil(5_000) { savedCount.intValue == 2 }

        composeRule.onNodeWithText("周报模型").assertIsDisplayed()
        composeRule.onNodeWithText("图片模型").assertIsDisplayed()
        composeRule.onNodeWithText("Key：••••1234").assertIsDisplayed()
        composeRule.onNodeWithText("Key：••••5678").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun httpModelRequiresExplicitConsentBeforeSave() {
        val fixture = fixture()
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiModelEditorScreen(fixture.viewModel, null, {}, {})
            }
        }
        composeRule.onNodeWithTag("ai_name").performTextInput("局域网模型")
        composeRule.onNodeWithTag("ai_base_url").performTextInput("http://192.168.1.9/v1")
        composeRule.onNodeWithTag("ai_model_id").performTextInput("local-model")
        composeRule.onNodeWithTag("ai_api_key").performTextInput("local-key")

        composeRule.onNodeWithTag("ai_model_save").performClick()
        composeRule.onNodeWithText("允许 HTTP 连接？").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_http_consent_confirm").performClick()
        composeRule.onNodeWithTag("ai_model_save").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.models.value.size == 1 }
        composeRule.runOnIdle {
            assertTrue(fixture.repository.models.value.single().allowInsecureHttp)
        }
    }

    @Test
    fun clickingTestDisablesBothCapabilitiesThenAddsBindingCandidateOnCompletion() {
        val client = SuspendedFlowAiClient()
        val fixture = fixture(
            model(1, "可控视觉", vision = true),
            client = client,
        )
        runBlocking { fixture.secrets.put("external-1", "sk-controlled") }
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiSettingsScreen(fixture.viewModel, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("ai_test_text_1").performClick()
        composeRule.waitUntil(5_000) { client.textStarted.isCompleted }
        composeRule.onNodeWithTag("ai_test_text_1").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_test_vision_1").assertIsNotEnabled()

        client.releaseText.complete(Unit)
        composeRule.waitUntil(5_000) {
            fixture.viewModel.state.value.models.single().textStatus == AiTestStatus.PASSED
        }
        composeRule.onNodeWithTag("ai_test_text_1").assertIsEnabled()
        composeRule.onNodeWithTag("ai_test_vision_1").assertIsEnabled()
        composeRule.onNodeWithText("功能绑定").performClick()
        composeRule.onNodeWithTag("binding_WEEKLY_REPORT").performClick()
        composeRule.onNodeWithText("可控视觉").assertIsDisplayed()
    }

    @Test
    fun twoModelsShowMaskedKeysCapabilityAndTestButtons() {
        val fixture = fixture(
            model(1, "周报模型", vision = false),
            model(2, "图片模型", vision = true),
            secretValues = mapOf(
                "external-1" to "sk-week-1234",
                "external-2" to "sk-image-5678",
            ),
        )

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiSettingsScreen(
                    viewModel = fixture.viewModel,
                    onOpenDrawer = {},
                    onAddModel = {},
                    onEditModel = {},
                )
            }
        }

        composeRule.onNodeWithText("周报模型").assertIsDisplayed()
        composeRule.onNodeWithText("图片模型").assertIsDisplayed()
        composeRule.onNodeWithText("Key：••••1234").assertIsDisplayed()
        composeRule.onNodeWithText("Key：••••5678").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_test_text_1").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_test_vision_1").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_test_vision_2").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("北京时间 2026-08-10 12:05").assertCountEquals(2)
    }

    @Test
    fun bindingTabFiltersModelsByAbilityAndPassedTest() {
        val fixture = fixture(
            model(1, "已验证图片", vision = true, text = AiTestStatus.PASSED, image = AiTestStatus.PASSED),
            model(2, "仅文本", vision = false, text = AiTestStatus.PASSED),
            model(3, "未验证图片", vision = true),
        )
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiSettingsScreen(fixture.viewModel, {}, {}, {})
            }
        }

        composeRule.onNodeWithText("功能绑定").performClick()
        composeRule.onNodeWithTag("binding_MEAL_CALORIE_ESTIMATE").performClick()
        composeRule.onNodeWithText("已验证图片").assertIsDisplayed()
        composeRule.onNodeWithText("仅文本").assertDoesNotExist()
        composeRule.onNodeWithText("未验证图片").assertDoesNotExist()
    }

    @Test
    fun deleteDialogListsBindingImpactBeforeConfirming() {
        val fixture = fixture(
            model(1, "共享模型", vision = true, text = AiTestStatus.PASSED, image = AiTestStatus.PASSED),
            bindings = listOf(
                AiFeatureBinding(AiFeature.WEEKLY_REPORT, 1, 1),
                AiFeatureBinding(AiFeature.MEAL_CALORIE_ESTIMATE, 1, 1),
            ),
        )
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiSettingsScreen(fixture.viewModel, {}, {}, {})
            }
        }

        composeRule.onNodeWithContentDescription("删除 共享模型").performClick()
        composeRule.onNodeWithText("删除模型配置？").assertIsDisplayed()
        composeRule.onNodeWithText("综合周报、图片热量估算将取消绑定", substring = true).assertIsDisplayed()
    }

    @Test
    fun drawerAiRootClearsPreviousChildBackStack() {
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                val navController = rememberNavController()
                val entry by navController.currentBackStackEntryAsState()
                Column {
                    NavHost(navController, startDestination = HabitDestination.Workbench.route) {
                        composable(HabitDestination.Workbench.route) {
                            Column {
                                Text("工作台")
                                Button({ navController.navigate("old_child") }) { Text("打开旧子页面") }
                            }
                        }
                        composable("old_child") { Text("旧子页面") }
                        composable(HabitDestination.AiSettings.route) {
                            Column {
                                Text("模型配置根页")
                                Button({ navController.popBackStack() }) { Text("返回") }
                            }
                        }
                    }
                    HabitDrawerContent(
                        selectedRoute = entry?.destination?.route,
                        progress = 0f,
                        habitExpanded = false,
                        dietExpanded = false,
                        aiExpanded = true,
                        onToggleHabit = {},
                        onToggleDiet = {},
                        onToggleAi = {},
                        onDestination = { navController.navigateFromDrawer(it) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("打开旧子页面").performClick()
        composeRule.onNodeWithText("旧子页面").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_ai_settings").performClick()
        composeRule.onNodeWithText("模型配置根页").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("工作台").assertIsDisplayed()
        composeRule.onNodeWithText("旧子页面").assertDoesNotExist()
    }

    private fun fixture(
        vararg models: AiModelConfig,
        bindings: List<AiFeatureBinding> = emptyList(),
        client: AiCompletionClient = FlowAiClient(),
        secretValues: Map<String, String> = emptyMap(),
    ): Fixture {
        val repository = FlowAiRepository(models.toList(), bindings)
        val secrets = FlowAiSecrets()
        runBlocking {
            secretValues.forEach { (externalId, apiKey) -> secrets.put(externalId, apiKey) }
        }
        val clock = Clock.fixed(Instant.parse("2026-08-10T04:05:06Z"), ZoneOffset.UTC)
        return Fixture(repository, secrets, AiSettingsViewModel(repository, secrets, client, clock))
    }

    private fun fillEditor(name: String, modelId: String, key: String) {
        composeRule.onNodeWithTag("ai_name").performTextInput(name)
        composeRule.onNodeWithTag("ai_base_url").performTextInput("https://api.example/v1")
        composeRule.onNodeWithTag("ai_model_id").performTextInput(modelId)
        composeRule.onNodeWithTag("ai_api_key").performTextInput(key)
    }

    private fun model(
        id: Long,
        name: String,
        vision: Boolean,
        text: AiTestStatus = AiTestStatus.UNTESTED,
        image: AiTestStatus = AiTestStatus.UNTESTED,
    ) = AiModelConfig(
        id = id,
        externalId = "external-$id",
        name = name,
        baseUrl = "https://api.example/v1",
        modelId = "model-$id",
        supportsText = true,
        supportsVision = vision,
        allowInsecureHttp = false,
        enabled = true,
        lastTestedAt = Instant.parse("2026-08-10T04:05:06Z").toEpochMilli(),
        lastTestStatus = if (image != AiTestStatus.UNTESTED) image else text,
        lastTestMessage = "habit-test-v1|text=${text.name}|vision=${image.name}|message=测试状态",
        createdAt = 1,
        updatedAt = 1,
    )
}

private data class Fixture(
    val repository: FlowAiRepository,
    val secrets: FlowAiSecrets,
    val viewModel: AiSettingsViewModel,
)

private class FlowAiRepository(
    initialModels: List<AiModelConfig>,
    initialBindings: List<AiFeatureBinding>,
) : AiModelRepository {
    val models = MutableStateFlow(initialModels)
    private val bindings = MutableStateFlow(initialBindings)
    private var nextId = (initialModels.maxOfOrNull { it.id } ?: 0) + 1
    override fun observeModels(): Flow<List<AiModelConfig>> = models
    override fun observeBindings(): Flow<List<AiFeatureBinding>> = bindings
    override fun observeModel(id: Long): Flow<AiModelConfig?> = MutableStateFlow(models.value.firstOrNull { it.id == id })
    override suspend fun saveModel(id: Long?, draft: AiModelConfigDraft): Long {
        val existing = id?.let { target -> models.value.firstOrNull { it.id == target } }
        val savedId = existing?.id ?: nextId++
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
            createdAt = existing?.createdAt ?: 1,
            updatedAt = 1,
        )
        models.value = models.value.filterNot { it.id == savedId } + saved
        return savedId
    }
    override suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long?) {
        models.value = models.value.map { if (it.id == id) it.copy(lastTestStatus = status, lastTestMessage = message, lastTestedAt = testedAt) else it }
    }
    override suspend fun bind(feature: AiFeature, modelId: Long?) {
        bindings.value = bindings.value.filterNot { it.feature == feature } + AiFeatureBinding(feature, modelId, 1)
    }
    override suspend fun deleteModel(id: Long) {
        models.value = models.value.filterNot { it.id == id }
        bindings.value = bindings.value.map { if (it.modelConfigId == id) it.copy(modelConfigId = null) else it }
    }
}

private class FlowAiSecrets : AiSecretStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun put(externalId: String, apiKey: String) { values[externalId] = apiKey }
    override suspend fun get(externalId: String): String? = values[externalId]
    override suspend fun maskedSuffix(externalId: String): String? = values[externalId]?.takeLast(4)
    override suspend fun remove(externalId: String) { values.remove(externalId) }
    override suspend fun clearAll() { values.clear() }
}

private class FlowAiClient : AiCompletionClient {
    override suspend fun completeText(model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String) = "OK"
    override suspend fun completeVision(model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String, images: List<AiPreparedImage>) = "OK"
}

private class SuspendedFlowAiClient : AiCompletionClient {
    val textStarted = CompletableDeferred<Unit>()
    val releaseText = CompletableDeferred<Unit>()

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
    ): String = "OK"
}
