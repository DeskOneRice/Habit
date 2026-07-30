package com.habit.app.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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

    @Before
    fun seedAndLaunch() {
        robot.resetDatabase()
        robot.seedHabit(name = "无障碍审计", iconKey = "book")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        robot.waitForTag("calendar_screen")
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    @Test
    fun everyMvpIconOnlyControlHasChineseDescriptionAnd48DpTarget() {
        assertAccessibleTag("calendar_previous_month", "上个月")
        assertAccessibleTag("calendar_next_month", "下个月")

        composeRule.onNodeWithTag("day_${robot.today()}").performClick()
        assertAccessibleTag("day_checkin_close", "关闭日期打卡面板")
        composeRule.onNodeWithTag("day_checkin_close").performClick()

        robot.navigateTo("习惯")
        composeRule.onNodeWithText("无障碍审计").performClick()
        assertAccessibleDescription("返回习惯列表")
        assertAccessibleTag("habit_detail_previous_month", "上个月")
        assertAccessibleTag("habit_detail_next_month", "下个月")

        composeRule.onNodeWithTag("edit_habit").performScrollTo().performClick()
        mapOf(
            "emoji_book" to "习惯图标：书本",
            "emoji_sprout" to "习惯图标：幼苗",
            "emoji_run" to "习惯图标：跑步",
            "emoji_heart" to "习惯图标：爱心",
            "emoji_water" to "习惯图标：喝水",
            "emoji_star" to "习惯图标：星星",
        ).forEach { (tag, description) ->
            assertAccessibleTag(tag, description)
        }
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
