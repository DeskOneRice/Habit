package com.habit.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NavigationMode { MENU, BACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTopAppBar(
    title: String,
    navigationMode: NavigationMode,
    onNavigation: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (navigationMode == NavigationMode.BACK) {
                LightweightArrowButton(
                    onClick = onNavigation,
                    direction = ArrowDirection.PREVIOUS,
                    contentDescription = "返回",
                    modifier = Modifier
                        .offset(x = (-4).dp)
                        .testTag("navigate_back"),
                )
            } else {
                TextButton(
                    onClick = onNavigation,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .offset(x = (-4).dp)
                        .semantics { contentDescription = "打开菜单" }
                        .testTag("open_drawer"),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("☰", fontSize = 26.sp)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
