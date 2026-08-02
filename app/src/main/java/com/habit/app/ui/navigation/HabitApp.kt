package com.habit.app.ui.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.habit.app.di.AppContainer
import com.habit.app.data.preferences.DrawerModuleGroup
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.welcome.FirstRunDestination
import com.habit.app.ui.welcome.WelcomeViewModel
import com.habit.app.ui.workbench.WorkbenchViewModel
import kotlinx.coroutines.launch

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
            FirstRunDestination.Workbench -> AppNavigation(HabitDestination.Workbench, container)
        }
    }
}

@Composable
private fun AppNavigation(startDestination: HabitDestination, container: AppContainer) {
    key(startDestination) {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val workbenchViewModel: WorkbenchViewModel = viewModel(
            factory = WorkbenchViewModelFactory(container),
        )
        val workbenchState by workbenchViewModel.state.collectAsStateWithLifecycle()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val dietPreferences by container.dietPreferencesRepository.preferences.collectAsStateWithLifecycle(
            initialValue = com.habit.app.data.preferences.DietPreferences(),
        )

        ModalNavigationDrawer(
            modifier = Modifier.testTag(
                "app_theme_primary_${MaterialTheme.colorScheme.primary.toArgb()}",
            ),
            drawerState = drawerState,
            gesturesEnabled = currentRoute in drawerTopLevelRoutes,
            drawerContent = {
                ModalDrawerSheet {
                    HabitDrawerContent(
                        selectedRoute = currentRoute,
                        progress = workbenchState.progress,
                        habitExpanded = dietPreferences.habitGroupExpanded,
                        dietExpanded = dietPreferences.dietGroupExpanded,
                        onToggleHabit = {
                            scope.launch { container.dietPreferencesRepository.setGroupExpanded(DrawerModuleGroup.HABIT, !dietPreferences.habitGroupExpanded) }
                        },
                        onToggleDiet = {
                            scope.launch { container.dietPreferencesRepository.setGroupExpanded(DrawerModuleGroup.DIET, !dietPreferences.dietGroupExpanded) }
                        },
                        onDestination = { destination ->
                            scope.launch { drawerState.close() }
                            navController.navigate(destination.route) {
                                popUpTo(HabitDestination.Workbench.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) {
            HabitNavHost(
                navController = navController,
                startDestination = startDestination,
                container = container,
                workbenchViewModel = workbenchViewModel,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )
        }
    }
}

private class WorkbenchViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WorkbenchViewModel(
            calendarRepository = container.calendarRepository,
            categoryRepository = container.categoryRepository,
            checkInRepository = container.checkInRepository,
            dateProvider = container.dateProvider,
            dietRepository = container.dietRepository,
        ) as T
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
