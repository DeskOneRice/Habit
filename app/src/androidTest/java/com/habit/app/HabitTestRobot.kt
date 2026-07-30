package com.habit.app

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
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

    fun seedCheckIns(habitId: Long, dates: List<LocalDate>) = runBlocking {
        dates.forEachIndexed { index, date ->
            container.database.checkInDao().insert(
                CheckInEntity(
                    habitId = habitId,
                    checkInEpochDay = date.toEpochDay(),
                    createdAt = 2_000L + index,
                    updatedAt = 2_000L + index,
                ),
            )
        }
    }

    fun habitThemeColor(habitId: Long): Long = runBlocking {
        container.database.habitDao().getById(habitId)!!.themeColor
    }

    fun habitId(name: String): Long = runBlocking {
        container.database.habitDao().observeAll().first().single { it.name == name }.id
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
        try {
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag(testTag, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError(
                "Timed out waiting for '$testTag'. Semantics:\n" +
                    rule.onRoot(useUnmergedTree = true).printToString(),
                failure,
            )
        }
    }

    fun waitForText(text: String, useUnmergedTree: Boolean = false) {
        try {
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText(text, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            throw AssertionError(
                "Timed out waiting for text '$text'. Semantics:\n" +
                    rule.onRoot(useUnmergedTree = true).printToString(),
                failure,
            )
        }
    }

    fun assertTagText(testTag: String, text: String) {
        waitForTag(testTag, useUnmergedTree = true)
        rule.onNodeWithTag(testTag, useUnmergedTree = true).assertTextEquals(text)
    }
}
