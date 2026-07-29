package com.habit.app

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.HabitEntity
import com.habit.app.data.local.PRESET_CATEGORIES
import com.habit.app.di.AppContainer
import com.habit.app.ui.theme.HabitThemeId
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class HabitTestRobot(val rule: ComposeContentTestRule) {
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

    fun seedHabit(name: String = "测试习惯"): Long = runBlocking {
        val category = container.database.categoryDao().observeVisible().first().first()
        container.database.habitDao().insert(
            HabitEntity(
                name = name,
                iconKey = "sprout",
                themeColor = 0xFF8DB9CC,
                categoryId = category.id,
                startEpochDay = LocalDate.of(2026, 7, 30).toEpochDay(),
                archivedEpochDay = null,
                sortOrder = 0,
                createdAt = 100L,
                updatedAt = 100L,
            ),
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
                    startEpochDay = LocalDate.of(2026, 7, 30).toEpochDay(),
                    archivedEpochDay = null,
                    sortOrder = index + 100,
                    createdAt = 100L + index,
                    updatedAt = 100L + index,
                ),
            )
        }
    }

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
}
