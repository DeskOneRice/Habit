package com.habit.app.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.colorScheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndToEndMvpTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val robot by lazy { HabitTestRobot(composeRule) }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun reset() = robot.resetDatabase()

    @After
    fun closeActivity() {
        scenario?.close()
    }

    @Test
    fun completeMvpJourneyPersistsHabitCheckInAndThemeAcrossRecreation() {
        val today = robot.today()
        val habitName = "端到端阅读"
        launch()

        robot.assertDisplayed("welcome_screen")
        robot.click("welcome_create")
        composeRule.onNodeWithTag("habit_name").performTextInput(habitName)
        composeRule.onNodeWithTag("emoji_star").performClick()
        composeRule.onNodeWithTag("habit_color_rose").performClick()
        composeRule.onNodeWithText("学习").performClick()
        composeRule.onNodeWithTag("save_habit").performClick()

        robot.waitForTag("habit_list_screen")
        robot.assertDisplayed("bottom_navigation")
        val habitId = robot.habitId(habitName)
        assertEquals(0xFFDF988F, robot.habitThemeColor(habitId))

        pressBackUnconditionally()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            scenario?.state?.isAtLeast(Lifecycle.State.STARTED) == false
        }
        assertFalse(
            "Back must finish onboarding instead of exposing its welcome/editor stack",
            scenario!!.state.isAtLeast(Lifecycle.State.STARTED),
        )

        scenario?.close()
        launch()
        robot.waitForTag("calendar_screen")
        checkInOnAggregateCalendar(today, habitId)

        robot.navigateTo("习惯")
        composeRule.onNodeWithText(habitName).performClick()
        assertPersistedDetail(today, habitName)
        composeRule.onNodeWithTag("edit_habit").performScrollTo().performClick()
        robot.assertDisplayed("habit_editor_screen")
        pressBackUnconditionally()
        robot.waitForTag("habit_detail_screen")
        composeRule
            .onNodeWithContentDescription("返回习惯列表")
            .performScrollTo()
            .performClick()
        robot.waitForTag("bottom_navigation")

        robot.navigateTo("设置")
        composeRule.onNodeWithTag("theme_SAGE_GREEN").performClick()
        composeRule.onNodeWithTag("theme_SAGE_GREEN_selected").assertIsSelected()
        val expectedPrimary = HabitThemeId.SAGE_GREEN.colorScheme().primary.toArgb()
        composeRule.onNodeWithTag("app_theme_primary_$expectedPrimary").assertIsDisplayed()

        scenario!!.recreate()
        robot.waitForTag("settings_screen")
        composeRule.onNodeWithTag("theme_SAGE_GREEN_selected").assertIsSelected()
        composeRule.onNodeWithTag("app_theme_primary_$expectedPrimary").assertIsDisplayed()
        robot.navigateTo("习惯")
        composeRule.onNodeWithText(habitName).performClick()
        assertPersistedDetail(today, habitName)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun checkInOnAggregateCalendar(today: LocalDate, habitId: Long) {
        robot.waitForTag("day_$today")
        composeRule.onNodeWithTag("day_$today").performClick()
        robot.waitForTag("checkin_habit_$habitId")
        composeRule.onNodeWithTag("checkin_habit_$habitId").performClick()
        robot.waitForText("已完成 1 / 1")
        composeRule.onNodeWithTag("day_checkin_close").performClick()
        robot.waitForTag("day_${today}_mark_0", useUnmergedTree = true)
        composeRule
            .onNodeWithTag("day_${today}_mark_0", useUnmergedTree = true)
            .assertTextEquals("⭐")
    }

    private fun assertPersistedDetail(today: LocalDate, habitName: String) {
        robot.waitForTag("habit_detail_screen")
        composeRule.onNodeWithText(habitName).assertIsDisplayed()
        composeRule.onNodeWithText("⭐").assertIsDisplayed()
        composeRule.onNodeWithText("学习").assertIsDisplayed()
        composeRule.onNodeWithTag("stat_total").assertTextEquals("1")
        composeRule.onNodeWithTag("stat_current").assertTextEquals("1")
        composeRule.onNodeWithTag("stat_longest").assertTextEquals("1")
        composeRule
            .onNodeWithTag("habit_day_${today}_completed")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
