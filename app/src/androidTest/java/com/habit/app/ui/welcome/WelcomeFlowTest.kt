package com.habit.app.ui.welcome

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { HabitTestRobot(composeRule) }

    @Before
    fun reset() = robot.resetDatabase()

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
    fun existingHabitStartsAtCalendarWithBottomNavigation() {
        robot.seedHabit()
        composeRule.activityRule.scenario.recreate()

        robot.assertDisplayed("calendar_screen")
        robot.assertDisplayed("bottom_navigation")
    }
}
