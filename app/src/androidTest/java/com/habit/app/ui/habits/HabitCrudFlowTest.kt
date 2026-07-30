package com.habit.app.ui.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitCrudFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { HabitTestRobot(composeRule) }

    @Before
    fun reset() = robot.resetDatabase()

    @Test
    fun createsHabitAndShowsItInCategoryList() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        composeRule.onNodeWithTag("habit_name").performTextInput("背单词")
        composeRule.onNodeWithTag("emoji_book").performClick()
        composeRule.onNodeWithText("学习").performClick()
        composeRule.onNodeWithTag("save_habit").performClick()

        composeRule.onNodeWithText("背单词").assertIsDisplayed()
        composeRule.onNodeWithText("学习").assertIsDisplayed()
    }

    @Test
    fun savingAnEditOpenedFromDetailReturnsToDetail() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        robot.createHabit("待编辑习惯", "emoji_book", "学习")
        composeRule.onNodeWithText("待编辑习惯").performClick()
        composeRule.onNodeWithTag("edit_habit").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_name").performTextClearance()
        composeRule.onNodeWithTag("habit_name").performTextInput("编辑后习惯")
        composeRule.onNodeWithTag("save_habit").performClick()

        composeRule.onNodeWithTag("habit_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithText("编辑后习惯").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invalidNameShowsInlineError() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        composeRule.onNodeWithTag("save_habit").performClick()

        composeRule.onNodeWithTag("habit_name_error").assertIsDisplayed()
    }

    @Test
    fun archiveThenDoubleConfirmDeleteRemovesHabitAndShowsHistoricalWarning() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        robot.createHabit("归档后删除", "emoji_book", "学习")
        composeRule.onNodeWithText("归档后删除").performClick()
        composeRule.onNodeWithTag("archive_habit").performScrollTo().performClick()
        composeRule.onNodeWithTag("archive_confirm").performClick()
        composeRule.onNodeWithText("已归档 (1)").performClick()
        composeRule.onNodeWithText("归档后删除").performClick()
        composeRule.onNodeWithTag("delete_habit").performScrollTo().performClick()
        composeRule.onNodeWithTag("delete_history_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_continue").performClick()
        composeRule.onNodeWithTag("delete_confirm").performClick()
        composeRule.onNodeWithText("归档后删除").assertDoesNotExist()
    }

    @Test
    fun migratesNonEmptyCustomCategoryBeforeDeletingIt() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        robot.createHabit("迁移习惯", "emoji_book", "学习")
        composeRule.onNodeWithText("管理分类").performClick()
        composeRule.onNodeWithText("新建分类").performClick()
        composeRule.onNodeWithText("分类名称").performTextInput("阅读")
        composeRule.onNodeWithText("保存").performClick()
        robot.waitForText("完成")
        composeRule.onNodeWithText("完成").performClick()
        composeRule.onNodeWithText("迁移习惯").performClick()
        composeRule.onNodeWithTag("edit_habit").performScrollTo().performClick()
        robot.waitForText("阅读")
        composeRule.onNodeWithText("阅读").performClick()
        composeRule.onNodeWithTag("save_habit").performClick()
        composeRule
            .onNodeWithContentDescription("返回习惯列表")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("管理分类").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithTag("category_migration_warning").assertIsDisplayed()
        composeRule.onAllNodesWithText("学习").filter(hasClickAction()).onFirst().performClick()
        composeRule.onNodeWithTag("category_migration_confirm").performClick()
        composeRule.onNodeWithText("阅读").assertDoesNotExist()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.onNodeWithText("学习").assertIsDisplayed()
        composeRule.onNodeWithText("迁移习惯").assertIsDisplayed()
    }

    @Test
    fun runningHabitKeepsItsDistinctEmojiInTheList() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        robot.createHabit("晨跑", "emoji_run", "运动")

        composeRule.onNodeWithText("🏃").assertIsDisplayed()
    }

    @Test
    fun colorPickerShowsWhichIdentificationColorIsSelected() {
        composeRule.onNodeWithTag("welcome_create").performClick()

        composeRule.onNodeWithTag("habit_color_rose").performClick()
        composeRule.onNodeWithTag("habit_color_rose").assertIsSelected()
    }

    @Test
    fun startDateControlOpensARealDatePicker() {
        composeRule.onNodeWithTag("welcome_create").performClick()

        composeRule.onNodeWithTag("start_date_picker").performScrollTo().performClick()
        composeRule.onNodeWithTag("start_date_dialog").assertIsDisplayed()
    }

    @Test
    fun longHabitListCanScrollToTheLastHabit() {
        composeRule.onNodeWithTag("welcome_create").performClick()
        robot.createHabit("首个习惯", "emoji_book", "学习")
        robot.seedHabits(24)

        composeRule
            .onNodeWithTag("habit_list_scroll")
            .performScrollToNode(hasText("长列表习惯 24"))
        composeRule.onNodeWithText("长列表习惯 24").assertIsDisplayed()
    }
}
