package com.habit.app.ui.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class CategoryAdaptiveLayoutTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val robot by lazy { HabitTestRobot(composeRule) }
    private var scenario: ActivityScenario<MainActivity>? = null
    private var sourceCategoryId: Long = 0
    private var lastTargetId: Long = 0

    @Before
    fun seedAndLaunch() {
        robot.resetDatabase()
        val habitId = robot.seedHabit(name = "分类滚动习惯")
        val targetIds = robot.seedCustomCategories(20)
        sourceCategoryId = robot.seedCustomCategories(1, namePrefix = "待迁移分类").single()
        lastTargetId = targetIds.last()
        robot.moveHabitToCategory(habitId, sourceCategoryId)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        robot.waitForTag("calendar_screen")
        robot.navigateTo("习惯")
        composeRule.onNodeWithText("管理分类").performClick()
        robot.waitForTag("category_screen")
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    @Test
    fun mainCategoryListScrollsToItsLastRow() {
        composeRule.onNodeWithText("待迁移分类 1")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun migrationTargetListScrollsToItsLastTarget() {
        composeRule.onNodeWithTag("category_delete_$sourceCategoryId")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("category_migration_target_$lastTargetId")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun mealAndBeverageSectionsShowTheirOwnPresets() {
        composeRule.onNodeWithTag("category_section_meal").performClick()
        composeRule.onNodeWithText("其他餐食").assertIsDisplayed()

        composeRule.onNodeWithTag("category_section_beverage").performClick()
        composeRule.onNodeWithText("咖啡").assertIsDisplayed()
    }
}
