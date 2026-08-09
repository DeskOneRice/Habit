package com.habit.app.ui.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarFlowTest {
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
    fun fiveCompletedHabitsUseTwoSlotEmojiAndOverflowRule() {
        val date = robot.today()
        robot.seedCompletedHabits(date, listOf("book", "sprout", "run", "heart", "water"))
        launchCalendar()

        robot.assertTagText("day_${date}_mark_0", "📚")
        robot.waitForTag("day_${date}_overflow", useUnmergedTree = true)
        composeRule.onNodeWithTag("day_${date}_overflow", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("+4").assertIsDisplayed()
    }

    @Test
    fun pastApplicableDateCanBeCheckedAndImmediatelyGainsMonthMark() {
        val date = robot.today().minusDays(1)
        val habitId = robot.seedHabit(startDate = date.minusDays(1))
        launchCalendar()
        moveToMonthContaining(date)

        robot.waitForTag("day_$date")
        composeRule.onNodeWithTag("day_$date").performClick()
        robot.waitForTag("checkin_habit_$habitId")
        composeRule.onNodeWithTag("checkin_habit_$habitId").performClick()
        composeRule.onNodeWithTag("day_checkin_close").performClick()

        robot.waitForTag("day_${date}_mark_0", useUnmergedTree = true)
        composeRule.onNodeWithTag("day_${date}_mark_0", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun futureDateIsViewableButCheckInControlIsDisabled() {
        val future = robot.today().plusDays(1)
        val habitId = robot.seedHabit(startDate = robot.today().minusDays(1))
        launchCalendar()
        moveToMonthContaining(future)

        robot.waitForTag("day_$future")
        composeRule.onNodeWithTag("day_$future").performClick()

        composeRule.onNodeWithTag("day_checkin_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("checkin_habit_$habitId").assertIsNotEnabled()
    }

    @Test
    fun everyEligibleHabitInLongDaySheetIsReachableByScrolling() {
        val date = robot.today()
        val habitIds = (1..18).map { index ->
            robot.seedHabit(
                name = "滚动习惯 $index",
                startDate = date.minusDays(1),
                sortOrder = index,
            )
        }
        launchCalendar()

        robot.waitForTag("day_$date")
        composeRule.onNodeWithTag("day_$date").performClick()
        robot.waitForTag("day_checkin_sheet")

        composeRule.onNodeWithTag("checkin_habit_${habitIds.last()}")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun launchCalendar() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        robot.waitForTag("workbench_screen")
        robot.navigateTo("日历")
        robot.waitForTag("calendar_screen")
    }

    private fun moveToMonthContaining(date: java.time.LocalDate) {
        val current = java.time.YearMonth.from(robot.today())
        val target = java.time.YearMonth.from(date)
        when {
            target.isBefore(current) -> composeRule.onNodeWithTag("calendar_previous_month").performClick()
            target.isAfter(current) -> composeRule.onNodeWithTag("calendar_next_month").performClick()
        }
    }
}
