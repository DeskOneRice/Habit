package com.habit.app.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityControlsTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val robot by lazy { HabitTestRobot(composeRule) }
    private var scenario: ActivityScenario<MainActivity>? = null
    private var habitId: Long = 0

    @Before
    fun seedAndLaunch() {
        robot.resetDatabase()
        habitId = robot.seedHabit(name = "无障碍审计", iconKey = "book")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        robot.waitForTag("workbench_screen")
        robot.navigateTo("日历")
        robot.waitForTag("calendar_screen")
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    @Test
    fun everyMvpIconOnlyControlHasChineseDescriptionAnd48DpTarget() {
        scenario!!.onActivity { activity ->
            assertEquals(360, activity.resources.configuration.screenWidthDp)
        }
        assertAccessibleTag("calendar_previous_month", "上个月")
        assertAccessibleTag("calendar_next_month", "下个月")

        composeRule.onNodeWithTag("day_${robot.today()}").performClick()
        assertAccessibleTag("day_checkin_close", "关闭日期打卡面板")
        assertAccessibleTag("checkin_habit_$habitId", "为无障碍审计补签")
        composeRule.onNodeWithTag("checkin_habit_$habitId").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithContentDescription("取消无障碍审计的打卡")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertAccessibleTag("checkin_habit_$habitId", "取消无障碍审计的打卡")
        composeRule.onNodeWithTag("day_checkin_close").performClick()

        robot.navigateTo("习惯")
        composeRule.onNodeWithText("无障碍审计").performClick()
        assertAccessibleDescription("返回")
        assertAccessibleTag("habit_detail_previous_month", "上个月")
        assertAccessibleTag("habit_detail_next_month", "下个月")

        composeRule.onNodeWithTag("edit_habit").performScrollTo().performClick()
        composeRule.onNodeWithTag("open_emoji_picker").performClick()
        mapOf(
            "emoji_emoji:📚" to "习惯图标：读书",
            "emoji_emoji:🧪" to "习惯图标：实验",
        ).forEach { (tag, description) -> assertAccessibleTag(tag, description) }
        composeRule.onNodeWithTag("emoji_category_sport").performScrollTo().performClick()
        assertAccessibleTag("emoji_emoji:🏃", "习惯图标：跑步")
        composeRule.onNodeWithTag("emoji_category_daily").performScrollTo().performClick()
        assertAccessibleTag("emoji_emoji:🌱", "习惯图标：早起")
        composeRule.onNodeWithTag("emoji_category_health").performScrollTo().performClick()
        assertAccessibleTag("emoji_emoji:💛", "习惯图标：心情")
        composeRule.onNodeWithTag("emoji_category_food").performScrollTo().performClick()
        assertAccessibleTag("emoji_emoji:💧", "习惯图标：喝水")
        composeRule.onNodeWithTag("emoji_category_hobby").performScrollTo().performClick()
        assertAccessibleTag("emoji_emoji:⭐", "习惯图标：收藏")
        composeRule.onNodeWithText("关闭").performClick()
        mapOf(
            "habit_color_sky" to "习惯颜色：天蓝",
            "habit_color_rose" to "习惯颜色：柔粉",
            "habit_color_sage" to "习惯颜色：鼠尾草绿",
            "habit_color_lavender" to "习惯颜色：雾紫",
            "habit_color_gray" to "习惯颜色：中性灰",
        ).forEach { (tag, description) ->
            assertAccessibleTag(tag, description)
        }
    }

    private fun assertAccessibleTag(tag: String, description: String) {
        composeRule.onNodeWithTag(tag)
            .assertContentDescriptionEquals(description)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    private fun assertAccessibleDescription(description: String) {
        composeRule.onNodeWithContentDescription(description)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
