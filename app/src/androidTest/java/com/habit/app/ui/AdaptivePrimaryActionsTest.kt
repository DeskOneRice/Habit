package com.habit.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.model.WeeklyReportCoverage
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptivePrimaryActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

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
        val report = adaptiveWeeklyReport()
        setShortScreenContent {
            WeeklyInsightCard(
                summary = WeeklyInsightSummary(
                    status = WeeklyInsightStatus.SAVED,
                    startDate = LocalDate.of(2026, 8, 3),
                    endDate = LocalDate.of(2026, 8, 9),
                    savedReport = report,
                ),
                onOpenReport = {},
                onOpenModelSettings = {},
            )
        }

        composeRule.onNodeWithText("生成于 8月10日 12:05").assertIsDisplayed()
    }

    private fun setShortScreenContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.3f),
            ) {
                HabitTheme(HabitThemeId.SKY_BLUE) {
                    Box(
                        Modifier
                            .requiredSize(width = 360.dp, height = 480.dp)
                            .clipToBounds(),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

private fun adaptiveWeeklyReport() = AiWeeklyReport(
    id = 1,
    startEpochDay = LocalDate.of(2026, 8, 3).toEpochDay(),
    endEpochDay = LocalDate.of(2026, 8, 9).toEpochDay(),
    generatedAt = Instant.parse("2026-08-10T04:05:00Z").toEpochMilli(),
    modelNameSnapshot = "测试模型",
    modelIdSnapshot = "test-model",
    title = "上周回顾",
    overview = "概览",
    habitAnalysis = "习惯",
    dietAnalysis = "饮食",
    correlationFinding = "关联",
    suggestions = listOf("一", "二", "三"),
    cautions = listOf("提示"),
    coverage = WeeklyReportCoverage(7, 4, 2, 2, 1, 1),
    createdAt = 1,
    updatedAt = 1,
)

private class StaticThemeRepository : ThemeRepository {
    override val theme: Flow<HabitThemeId> = flowOf(HabitThemeId.SKY_BLUE)

    override suspend fun setTheme(theme: HabitThemeId) = Unit
}
