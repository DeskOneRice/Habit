package com.habit.app.ui.welcome

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { HabitTestRobot(composeRule) }

    @Before
    fun reset() {
        robot.resetDatabase()
        composeRule.activityRule.scenario.recreate()
        robot.waitForTag("welcome_screen")
    }

    @Test
    fun emptyDatabaseShowsWelcomeAndCreateAction() {
        composeRule.onNodeWithText("把每一天，积攒成喜欢的样子").assertIsDisplayed()
        composeRule.onNodeWithText("创建我的第一个习惯").assertHasClickAction()
        composeRule.onNodeWithTag("bottom_navigation").assertDoesNotExist()
    }

    @Test
    fun createActionNavigatesToEditorWithoutBottomNavigation() {
        robot.click("welcome_create")

        robot.assertDisplayed("habit_editor_screen")
        composeRule.onNodeWithText("日历").assertDoesNotExist()
    }

    @Test
    fun existingHabitStartsAtWorkbenchWithSideDrawerNavigation() {
        robot.seedHabit()
        composeRule.activityRule.scenario.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            robot.assertDisplayed("workbench_screen")
            robot.assertDisplayed("open_drawer")
            composeRule.onNodeWithTag("bottom_navigation").assertDoesNotExist()
        }
    }

    @Test
    fun backFromFirstCreatedHabitListDoesNotReturnToCompletedOnboarding() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        composeRule.onNodeWithTag("habit_name").performTextInput("第一个习惯")
        robot.selectEmoji("emoji_book")
        composeRule.onNodeWithText("学习").performClick()
        composeRule.onNodeWithTag("save_habit").performClick()
        robot.waitForTag("workbench_screen")

        pressBackUnconditionally()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            !composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.STARTED)
        }
        assertTrue(
            "Back should finish the onboarding task instead of revealing welcome/editor",
            !composeRule.activityRule.scenario.state.isAtLeast(Lifecycle.State.STARTED),
        )
    }
}
