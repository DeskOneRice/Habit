package com.habit.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitTopAppBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backNavigationUsesLargeLightweightChevron() {
        composeRule.setContent {
            MaterialTheme {
                HabitTopAppBar(
                    title = "习惯详情",
                    navigationMode = NavigationMode.BACK,
                    onNavigation = {},
                )
            }
        }

        composeRule.onNodeWithTag("navigate_back")
            .assertContentDescriptionEquals("返回")
            .assertTextContains("‹")
    }

    @Test
    fun menuNavigationRemainsVisuallyDistinct() {
        composeRule.setContent {
            MaterialTheme {
                HabitTopAppBar(
                    title = "今日工作台",
                    navigationMode = NavigationMode.MENU,
                    onNavigation = {},
                )
            }
        }

        composeRule.onNodeWithTag("open_drawer")
            .assertContentDescriptionEquals("打开菜单")
            .assertTextContains("☰")
    }
}
