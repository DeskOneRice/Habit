package com.habit.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun commonTopActionUsesSmallTextAndAccessibleTouchTarget() {
        composeRule.setContent {
            MaterialTheme {
                HabitTopAppBar(
                    title = "饮食详情",
                    navigationMode = NavigationMode.BACK,
                    onNavigation = {},
                ) {
                    HabitTopAction(
                        text = "编辑",
                        contentDescription = "编辑饮食记录",
                        onClick = {},
                        modifier = Modifier.testTag("top_action_edit"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("navigate_back")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("top_action_edit")
            .assertContentDescriptionEquals("编辑饮食记录")
            .assertTextContains("编辑")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
