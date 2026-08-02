package com.habit.app.ui.components

import androidx.compose.foundation.layout.RowScope
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

enum class NavigationMode { MENU, BACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTopAppBar(
    title: String,
    navigationMode: NavigationMode,
    onNavigation: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val description = if (navigationMode == NavigationMode.MENU) "打开菜单" else "返回"
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TextButton(
                onClick = onNavigation,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = description }
                    .testTag(if (navigationMode == NavigationMode.MENU) "open_drawer" else "navigate_back"),
            ) {
                Text(if (navigationMode == NavigationMode.MENU) "☰" else "‹")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
