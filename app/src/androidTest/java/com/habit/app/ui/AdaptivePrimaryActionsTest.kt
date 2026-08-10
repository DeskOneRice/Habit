package com.habit.app.ui

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habit.app.ui.settings.SettingsScreen
import com.habit.app.ui.settings.SettingsViewModel
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.ThemeRepository
import com.habit.app.ui.welcome.WelcomeScreen
import com.habit.app.ui.workbench.RecentDay
import com.habit.app.ui.workbench.RecentWeekTimeline
import com.habit.app.ui.workbench.WeeklyInsightStatus
import com.habit.app.ui.workbench.WeeklyInsightSummary
import com.habit.app.ui.workbench.WeeklyInsightCard
import com.habit.app.ui.ai.AiWeeklyReportScreen
import com.habit.app.ui.ai.AiWeeklyReportDetailScreen
import com.habit.app.ui.ai.AiWeeklyReportState
import com.habit.app.ui.ai.weeklySavedReport
import com.habit.app.ui.ai.weeklyTestFixture
import com.habit.app.ui.components.NavigationMode
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptivePrimaryActionsTest {
    private val composeRule = createComposeRule()
    private val trueShortDisplay = TrueShortDisplayRule()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(trueShortDisplay).around(composeRule)

    @Test
    fun welcomePrimaryActionRemainsReachableOnShort360DpScreenAt130PercentFontScale() {
        setShortScreenContent {
            WelcomeScreen(onCreateHabit = {}, onSkip = {})
        }

        composeRule.onNodeWithTag("welcome_create")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    @Test
    fun settingsBackupActionCanBeReachedOnShort360DpScreenAt130PercentFontScale() {
        val viewModel = SettingsViewModel(StaticThemeRepository())
        setShortScreenContent {
            SettingsScreen(viewModel = viewModel)
        }

        composeRule.onNodeWithTag("export_data")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    @Test
    fun recentWeekTimelineKeepsAllSevenDaysInOneHorizontalRow() {
        val today = LocalDate.of(2026, 8, 7)
        val days = (6 downTo 0).map { offset ->
            RecentDay(
                date = today.minusDays(offset.toLong()),
                iconKeys = List(offset % 4) { "book" },
            )
        }
        setShortScreenContent {
            RecentWeekTimeline(days = days, today = today)
        }

        val bounds = days.map { day ->
            composeRule.onNodeWithTag("recent_day_${day.date}")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        }
        assertTrue(bounds.zipWithNext().all { (left, right) -> left.left < right.left })
        assertTrue(bounds.maxOf { it.center.y } - bounds.minOf { it.center.y } <= 1f)
    }

    @Test
    fun weeklyInsightPrimaryActionRemainsReachableAtLargeFontScale() {
        setShortScreenContent {
            WeeklyInsightCard(
                summary = WeeklyInsightSummary(
                    status = WeeklyInsightStatus.READY_TO_GENERATE,
                    startDate = LocalDate.of(2026, 8, 3),
                    endDate = LocalDate.of(2026, 8, 9),
                ),
                onOpenReport = {},
                onOpenModelSettings = {},
            )
        }

        composeRule.onNodeWithTag("weekly_insight_action")
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    @Test
    fun savedWeeklyInsightShowsBeijingGenerationTime() {
        setShortScreenContent {
            WeeklyInsightCard(
                summary = WeeklyInsightSummary(
                    status = WeeklyInsightStatus.SAVED,
                    startDate = LocalDate.of(2026, 8, 3),
                    endDate = LocalDate.of(2026, 8, 9),
                    detailStartEpochDay = LocalDate.of(2026, 8, 3).toEpochDay(),
                    title = "上周回顾",
                    overview = "概览",
                    generatedAt = Instant.parse("2026-08-10T04:05:00Z").toEpochMilli(),
                ),
                onOpenReport = {},
                onOpenModelSettings = {},
            )
        }

        composeRule.onNodeWithText("生成于 8月10日 12:05").assertIsDisplayed()
    }

    @Test
    fun weeklyReportRootActionIsReachableOnShortLargeFontScreen() {
        val fixture = weeklyTestFixture()
        setShortScreenContent {
            AiWeeklyReportScreen(fixture.viewModel, NavigationMode.MENU, {}, {}, {}, {})
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.ReadyToGenerate }

        composeRule.onNodeWithTag("weekly_report_generate")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    @Test
    fun weeklyReportPreviewSaveIsReachableOnShortLargeFontScreen() {
        val fixture = weeklyTestFixture()
        setShortScreenContent {
            AiWeeklyReportScreen(fixture.viewModel, NavigationMode.MENU, {}, {}, {}, {})
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.ReadyToGenerate }
        composeRule.onNodeWithTag("weekly_report_generate").performClick()
        composeRule.onNodeWithTag("weekly_report_generate_confirm").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Preview }

        composeRule.onNodeWithTag("weekly_report_save")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    @Test
    fun weeklyReportReplacementDialogConfirmIsReachableOnShortLargeFontScreen() {
        val old = weeklySavedReport(LocalDate.of(2026, 8, 3), "旧周报", 8)
        val fixture = weeklyTestFixture(listOf(old))
        val observed = AtomicReference<ObservedConfiguration>()
        composeRule.setContent {
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            SideEffect {
                observed.set(
                    ObservedConfiguration(
                        widthDp = configuration.screenWidthDp,
                        heightDp = configuration.screenHeightDp,
                        fontScale = density.fontScale,
                    ),
                )
            }
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiWeeklyReportScreen(fixture.viewModel, NavigationMode.MENU, {}, {}, {}, {})
            }
        }
        composeRule.waitUntil(5_000) {
            observed.get()?.let { it.widthDp == 360 && it.heightDp <= 480 && it.fontScale >= 1.29f } == true
        }
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.ReadyToGenerate }
        composeRule.onNodeWithTag("weekly_report_generate").performClick()
        composeRule.onNodeWithTag("weekly_report_generate_confirm").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.state.value is AiWeeklyReportState.Preview }
        composeRule.onNodeWithTag("weekly_report_save").performScrollTo().performClick()

        composeRule.onNodeWithTag("weekly_report_replace_confirm")
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    @Test
    fun weeklyReportDetailBackIsReachableOnShortLargeFontScreen() {
        setShortScreenContent {
            AiWeeklyReportDetailScreen(
                report = weeklySavedReport(LocalDate.of(2026, 8, 3), "详情周报", 9),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("navigate_back")
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    private fun setShortScreenContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                content()
            }
        }
    }
}

private data class ObservedConfiguration(
    val widthDp: Int,
    val heightDp: Int,
    val fontScale: Float,
)

private class TrueShortDisplayRule : ExternalResource() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var original: DisplayConfiguration

    override fun before() {
        original = captureDisplayConfiguration()
        shell("wm size 720x960")
        shell("wm density 320")
        shell("settings put system font_scale 1.3")
        shell("am wait-for-broadcast-idle")
        instrumentation.waitForIdleSync()
    }

    override fun after() {
        shell(original.overrideSize?.let { "wm size $it" } ?: "wm size reset")
        shell(original.overrideDensity?.let { "wm density $it" } ?: "wm density reset")
        shell("settings put system font_scale ${original.fontScale}")
        shell("am wait-for-broadcast-idle")
        instrumentation.waitForIdleSync()
    }

    private fun captureDisplayConfiguration(): DisplayConfiguration {
        val sizeOutput = shell("wm size")
        val densityOutput = shell("wm density")
        return DisplayConfiguration(
            overrideSize = Regex("""Override size:\s*(\d+x\d+)""").find(sizeOutput)?.groupValues?.get(1),
            overrideDensity = Regex("""Override density:\s*(\d+)""").find(densityOutput)?.groupValues?.get(1),
            fontScale = shell("settings get system font_scale").trim(),
        )
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return try {
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        } finally {
            descriptor.close()
        }
    }

    private data class DisplayConfiguration(
        val overrideSize: String?,
        val overrideDensity: String?,
        val fontScale: String,
    )
}

private class StaticThemeRepository : ThemeRepository {
    override val theme: Flow<HabitThemeId> = flowOf(HabitThemeId.SKY_BLUE)

    override suspend fun setTheme(theme: HabitThemeId) = Unit
}
