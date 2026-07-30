package com.habit.app

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.CheckInEntity
import com.habit.app.data.local.HabitEntity
import com.habit.app.data.local.PRESET_CATEGORIES
import com.habit.app.di.AppContainer
import com.habit.app.ui.theme.HabitThemeId
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class HabitTestRobot(val rule: ComposeTestRule) {
    private val application: HabitApplication
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as HabitApplication

    val container: AppContainer
        get() = application.container

    fun resetDatabase() = runBlocking {
        container.database.clearAllTables()
        val now = 100L
        PRESET_CATEGORIES.forEachIndexed { index, name ->
            container.database.categoryDao().insert(
                CategoryEntity(
                    name = name,
                    isPreset = true,
                    isHidden = false,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        container.themeRepository.setTheme(HabitThemeId.SKY_BLUE)
    }

    fun today(): LocalDate = container.dateProvider.today()

    fun seedHabit(
        name: String = "测试习惯",
        iconKey: String = "sprout",
        startDate: LocalDate = today(),
        sortOrder: Int = 0,
    ): Long = runBlocking {
        val category = container.database.categoryDao().observeVisible().first().first()
        insertHabit(
            name = name,
            iconKey = iconKey,
            categoryId = category.id,
            startDate = startDate,
            sortOrder = sortOrder,
        )
    }

    fun seedHabits(count: Int, namePrefix: String = "长列表习惯") = runBlocking {
        val category = container.database.categoryDao().observeVisible().first().first()
        repeat(count) { index ->
            container.database.habitDao().insert(
                HabitEntity(
                    name = "$namePrefix ${index + 1}",
                    iconKey = "sprout",
                    themeColor = 0xFF8DB9CC,
                    categoryId = category.id,
                    startEpochDay = today().toEpochDay(),
                    archivedEpochDay = null,
                    sortOrder = index + 100,
                    createdAt = 100L + index,
                    updatedAt = 100L + index,
                ),
            )
        }
    }

    fun seedCompletedHabits(date: LocalDate, iconKeys: List<String>): List<Long> = runBlocking {
        val category = container.database.categoryDao().observeVisible().first().first()
        iconKeys.mapIndexed { index, iconKey ->
            val habitId = insertHabit(
                name = "日历习惯 ${index + 1}",
                iconKey = iconKey,
                categoryId = category.id,
                startDate = date.minusDays(1),
                sortOrder = index,
            )
            container.database.checkInDao().insert(
                CheckInEntity(
                    habitId = habitId,
                    checkInEpochDay = date.toEpochDay(),
                    createdAt = 1_000L + index,
                    updatedAt = 1_000L + index,
                ),
            )
            habitId
        }
    }

    private suspend fun insertHabit(
        name: String,
        iconKey: String,
        categoryId: Long,
        startDate: LocalDate,
        sortOrder: Int,
    ): Long = container.database.habitDao().insert(
        HabitEntity(
            name = name,
            iconKey = iconKey,
            themeColor = 0xFF8DB9CC,
            categoryId = categoryId,
            startEpochDay = startDate.toEpochDay(),
            archivedEpochDay = null,
            sortOrder = sortOrder,
            createdAt = 100L,
            updatedAt = 100L,
        ),
    )

    fun navigateTo(label: String) {
        rule.onNodeWithText(label).performClick()
    }

    fun click(testTag: String) {
        rule.onNodeWithTag(testTag).performClick()
    }

    fun assertDisplayed(testTag: String) {
        rule.onNodeWithTag(testTag).assertIsDisplayed()
    }

    fun createHabit(name: String, emojiTag: String, category: String) {
        rule.onNodeWithTag("habit_name").performTextInput(name)
        rule.onNodeWithTag(emojiTag).performClick()
        rule.onNodeWithText(category).performClick()
        rule.onNodeWithTag("save_habit").performClick()
    }

    fun assertTextVisible(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun waitForTag(testTag: String, useUnmergedTree: Boolean = false) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(testTag, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun assertTagText(testTag: String, text: String) {
        waitForTag(testTag, useUnmergedTree = true)
        rule.onNodeWithTag(testTag, useUnmergedTree = true).assertTextEquals(text)
    }
}
