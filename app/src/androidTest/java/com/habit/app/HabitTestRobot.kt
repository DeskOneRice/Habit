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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.CheckInEntity
import com.habit.app.data.local.HabitEntity
import com.habit.app.data.local.PRESET_CATEGORIES
import com.habit.app.data.local.DietCategoryEntity
import com.habit.app.domain.model.DIET_CATEGORY_PRESETS
import com.habit.app.di.AppContainer
import com.habit.app.di.themeDataStore
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
        application.themeDataStore.edit { preferences -> preferences.clear() }
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
        container.database.dietCategoryDao().insertAll(
            DIET_CATEGORY_PRESETS.mapIndexed { index, preset ->
                DietCategoryEntity(
                    id = preset.id,
                    scope = preset.scope.name,
                    name = preset.name,
                    isPreset = true,
                    isHidden = false,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
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

    fun seedCustomCategories(count: Int, namePrefix: String = "自定义分类"): List<Long> = runBlocking {
        repeat(count) { index ->
            container.database.categoryDao().insert(
                CategoryEntity(
                    name = "$namePrefix ${index + 1}",
                    isPreset = false,
                    isHidden = false,
                    sortOrder = 100 + index,
                    createdAt = 500L + index,
                    updatedAt = 500L + index,
                ),
            )
        }.let {
            container.database.categoryDao()
                .observeAll()
                .first()
                .filter { category -> category.name.startsWith(namePrefix) }
                .map(CategoryEntity::id)
        }
    }

    fun moveHabitToCategory(habitId: Long, categoryId: Long) = runBlocking {
        container.database.habitDao().reassignCategory(
            habitId = habitId,
            categoryId = categoryId,
            updatedAt = 900L,
        )
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

    fun habitCategoryId(name: String): Long = runBlocking {
        container.database.categoryDao().observeAll().first().single { it.name == name }.id
    }

    fun visibleHabitCategoryIds(): List<Long> = runBlocking {
        container.database.categoryDao().observeVisible().first().map(CategoryEntity::id)
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
        val destinationTag = when (label) {
            "工作台", "今日工作台" -> "drawer_workbench"
            "日历", "习惯日历" -> "drawer_calendar"
            "习惯", "我的习惯" -> "drawer_habits"
            "分类", "分类管理" -> "drawer_categories_drawer"
            "饮食", "饮食日记" -> "drawer_diet"
            "饮食模板" -> "drawer_diet_templates"
            "饮食统计" -> "drawer_diet_stats"
            "饮食设置" -> "drawer_diet_settings"
            "设置", "主题与设置" -> "drawer_settings"
            else -> error("未知侧边栏入口：$label")
        }
        waitForTag("open_drawer")
        rule.onNodeWithTag("open_drawer").performClick()
        waitForTag(destinationTag)
        rule.onNodeWithTag(destinationTag).performScrollTo().performClick()
    }

    fun click(testTag: String) {
        rule.onNodeWithTag(testTag).performClick()
    }

    fun assertDisplayed(testTag: String) {
        rule.onNodeWithTag(testTag).assertIsDisplayed()
    }

    fun createHabit(name: String, emojiTag: String, category: String) {
        rule.onNodeWithTag("habit_name").performTextInput(name)
        selectEmoji(emojiTag)
        rule.onNodeWithText(category).performClick()
        rule.onNodeWithTag("save_habit").performClick()
        waitForTag("workbench_screen")
        navigateTo("习惯")
        waitForTag("habit_list_screen")
    }

    fun selectEmoji(legacyTag: String) {
        val (categoryTag, pickerTag) = when (legacyTag) {
            "emoji_book" -> "emoji_category_study" to "emoji_emoji:📚"
            "emoji_sprout" -> "emoji_category_daily" to "emoji_emoji:🌱"
            "emoji_run" -> "emoji_category_sport" to "emoji_emoji:🏃"
            "emoji_heart" -> "emoji_category_health" to "emoji_emoji:💛"
            "emoji_water" -> "emoji_category_food" to "emoji_emoji:💧"
            "emoji_star" -> "emoji_category_hobby" to "emoji_emoji:⭐"
            else -> error("未知测试 Emoji：$legacyTag")
        }
        rule.onNodeWithTag("open_emoji_picker").performClick()
        waitForTag("emoji_picker_sheet")
        rule.onNodeWithTag(categoryTag).performScrollTo().performClick()
        waitForTag(pickerTag)
        rule.onNodeWithTag(pickerTag).performClick()
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
