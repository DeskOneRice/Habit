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
    fun settingsPrimaryActionCanBeReachedOnShort360DpScreenAt130PercentFontScale() {
        val viewModel = SettingsViewModel(StaticThemeRepository())
        setShortScreenContent {
            SettingsScreen(viewModel = viewModel, onCategories = {})
        }

        composeRule.onNodeWithTag("settings_categories")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
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

private class StaticThemeRepository : ThemeRepository {
    override val theme: Flow<HabitThemeId> = flowOf(HabitThemeId.SKY_BLUE)

    override suspend fun setTheme(theme: HabitThemeId) = Unit
}
