package com.habit.app.ui.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import java.time.YearMonth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDetailTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { HabitTestRobot(composeRule) }

    @Before
    fun reset() = robot.resetDatabase()

    @Test
    fun detailShowsStatisticsCalendarMonthNavigationAndExplicitEditPath() {
        val today = robot.today()
        val habitId = robot.seedHabit(
            name = "背单词",
            iconKey = "book",
            startDate = today.minusDays(4),
        )
        robot.seedCheckIns(habitId, listOf(today.minusDays(2), today.minusDays(1)))
        composeRule.activityRule.scenario.recreate()

        robot.navigateTo("习惯")
        composeRule.onNodeWithText("背单词").performClick()

        composeRule.onNodeWithTag("habit_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithText("📚").assertIsDisplayed()
        composeRule.onNodeWithText("学习").assertIsDisplayed()
        composeRule.onNodeWithTag("stat_total").assertTextEquals("2")
        composeRule.onNodeWithTag("stat_current").assertTextEquals("2")
        composeRule.onNodeWithTag("stat_longest").assertTextEquals("2")
        composeRule
            .onNodeWithTag("habit_day_${today.minusDays(1)}_completed")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("habit_day_$today").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("habit_detail_previous_month").performScrollTo().performClick()
        val previousMonth = YearMonth.from(today).minusMonths(1)
        composeRule
            .onNodeWithTag("habit_detail_month_title")
            .assertTextEquals("${previousMonth.year}年${previousMonth.monthValue}月")
        composeRule.onNodeWithTag("habit_detail_next_month").performClick()

        composeRule.onNodeWithTag("edit_habit").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_editor_screen").assertIsDisplayed()
    }
}
