package com.habit.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.habit.app.di.AppContainer
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.welcome.FirstRunDestination
import com.habit.app.ui.welcome.WelcomeViewModel

private data class BottomDestination(
    val destination: HabitDestination,
    val label: String,
)

private val bottomDestinations = listOf(
    BottomDestination(HabitDestination.Calendar, "日历"),
    BottomDestination(HabitDestination.Habits, "习惯"),
    BottomDestination(HabitDestination.Settings, "设置"),
)

@Composable
fun HabitApp(container: AppContainer) {
    val themeId by container.themeRepository.theme.collectAsStateWithLifecycle(
        initialValue = HabitThemeId.SKY_BLUE,
    )
    val viewModel: WelcomeViewModel = viewModel(
        factory = WelcomeViewModelFactory(container),
    )
    val firstRunDestination by viewModel.destination.collectAsStateWithLifecycle(
        initialValue = FirstRunDestination.Loading,
    )
    var initialDestination by remember { mutableStateOf<FirstRunDestination?>(null) }
    if (initialDestination == null && firstRunDestination != FirstRunDestination.Loading) {
        initialDestination = firstRunDestination
    }

    HabitTheme(themeId) {
        when (initialDestination ?: FirstRunDestination.Loading) {
            FirstRunDestination.Loading -> Unit
            FirstRunDestination.Welcome -> AppNavigation(HabitDestination.Welcome, container)
            FirstRunDestination.Calendar -> AppNavigation(HabitDestination.Calendar, container)
        }
    }
}

@Composable
private fun AppNavigation(startDestination: HabitDestination, container: AppContainer) {
    key(startDestination) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomNavigation = bottomDestinations.any { it.destination.route == currentRoute }

        Scaffold(
            bottomBar = {
                if (showBottomNavigation) {
                    NavigationBar(Modifier.testTag("bottom_navigation")) {
                        bottomDestinations.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.destination.route,
                                onClick = {
                                    navController.navigate(item.destination.route) {
                                        launchSingleTop = true
                                    }
                                },
                                icon = {},
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            androidx.compose.foundation.layout.Box(Modifier.padding(paddingValues)) {
                HabitNavHost(navController = navController, startDestination = startDestination, container = container)
            }
        }
    }
}

private class WelcomeViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WelcomeViewModel::class.java))
        return WelcomeViewModel(container.habitRepository) as T
    }
}
