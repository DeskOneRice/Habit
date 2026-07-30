package com.habit.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.colorScheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePersistenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { HabitTestRobot(composeRule) }

    @Before
    fun reset() {
        robot.resetDatabase()
        robot.seedHabit(name = "主题验证")
        composeRule.activityRule.scenario.recreate()
        robot.waitForTag("bottom_navigation")
    }

    @Test
    fun selectionAppliesImmediatelyAndSurvivesActivityRecreation() {
        robot.navigateTo("设置")
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("theme_SAGE_GREEN").performClick()
        composeRule.onNodeWithTag("theme_SAGE_GREEN_selected").assertIsSelected()
        val expectedPrimary = HabitThemeId.SAGE_GREEN.colorScheme().primary.toArgb()
        composeRule
            .onNodeWithTag("app_theme_primary_$expectedPrimary")
            .assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        robot.waitForTag("settings_screen")

        composeRule.onNodeWithTag("theme_SAGE_GREEN_selected").assertIsSelected()
    }

    @Test
    fun categoryManagementIsReachableFromSettings() {
        robot.navigateTo("设置")

        composeRule.onNodeWithTag("settings_categories").performScrollTo().performClick()

        robot.waitForTag("category_screen")
        composeRule.onNodeWithTag("category_screen").assertIsDisplayed()
    }

    @Test
    fun changingGlobalThemeDoesNotRewriteHabitIdentificationColor() {
        val habitId = robot.seedHabit(name = "颜色独立")
        val before = robot.habitThemeColor(habitId)
        robot.navigateTo("设置")

        composeRule.onNodeWithTag("theme_${HabitThemeId.MIST_PURPLE}").performClick()

        org.junit.Assert.assertEquals(before, robot.habitThemeColor(habitId))
    }
}
