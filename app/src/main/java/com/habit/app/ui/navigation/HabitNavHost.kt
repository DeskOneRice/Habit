package com.habit.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.habit.app.di.AppContainer
import com.habit.app.ui.calendar.CalendarScreen
import com.habit.app.ui.calendar.CalendarViewModel
import com.habit.app.ui.categories.CategoryScreen
import com.habit.app.ui.categories.CategoryViewModel
import com.habit.app.ui.habits.HabitDetailScreen
import com.habit.app.ui.habits.HabitDetailViewModel
import com.habit.app.ui.habits.HabitEditorScreen
import com.habit.app.ui.habits.HabitEditorViewModel
import com.habit.app.ui.habits.HabitListScreen
import com.habit.app.ui.habits.HabitListViewModel
import com.habit.app.ui.settings.SettingsScreen
import com.habit.app.ui.settings.SettingsViewModel
import com.habit.app.ui.welcome.WelcomeScreen
import com.habit.app.ui.workbench.WorkbenchScreen
import com.habit.app.ui.workbench.WorkbenchViewModel

@Composable
fun HabitNavHost(
    navController: NavHostController,
    startDestination: HabitDestination,
    container: AppContainer,
    workbenchViewModel: WorkbenchViewModel,
    onOpenDrawer: () -> Unit,
) {
    NavHost(navController, startDestination.route) {
        composable(HabitDestination.Welcome.route) {
            WelcomeScreen {
                navController.navigate(HabitDestination.HabitEditor.route())
            }
        }
        composable(HabitDestination.Workbench.route) {
            WorkbenchScreen(
                viewModel = workbenchViewModel,
                onOpenDrawer = onOpenDrawer,
                onCreateHabit = { navController.navigate(HabitDestination.HabitEditor.route()) },
                onOpenCalendar = { navController.navigate(HabitDestination.Calendar.route) },
                onOpenHabit = { navController.navigate(HabitDestination.HabitDetail.route(it)) },
            )
        }
        composable(HabitDestination.Calendar.route) {
            CalendarScreen(viewModel(factory = CalendarFactory(container)), onOpenDrawer)
        }
        composable(HabitDestination.Habits.route) {
            HabitListScreen(
                viewModel = viewModel(factory = ListFactory(container)),
                onCreate = {
                    navController.navigate(HabitDestination.HabitEditor.route())
                },
                onOpenDetail = { habitId ->
                    navController.navigate(HabitDestination.HabitDetail.route(habitId))
                },
                onCategories = {
                    navController.navigate(HabitDestination.Categories.route)
                },
                onOpenDrawer = onOpenDrawer,
            )
        }
        composable(
            route = HabitDestination.HabitEditor.route,
            arguments = listOf(
                navArgument("habitId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val habitId = entry.arguments
                ?.getLong("habitId")
                ?.takeIf { it >= 0 }
            val model: HabitEditorViewModel = viewModel(
                factory = EditorFactory(container, habitId),
            )
            HabitEditorScreen(
                viewModel = model,
                categories = container.categoryRepository,
                emojiPreferences = container.emojiPreferencesRepository,
                onSaved = {
                    if (habitId == null) {
                        if (
                            navController.previousBackStackEntry
                                ?.destination
                                ?.route == HabitDestination.Welcome.route
                        ) {
                            navController.completeOnboarding()
                        } else {
                            navController.popBackStack()
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
                onArchive = { id ->
                    model.archive(id) { navController.returnToHabits() }
                },
                onDelete = { id ->
                    model.delete(id) { navController.returnToHabits() }
                },
            )
        }
        composable(
            route = HabitDestination.HabitDetail.route,
            arguments = listOf(
                navArgument("habitId") {
                    type = NavType.LongType
                },
            ),
        ) { entry ->
            val habitId = requireNotNull(entry.arguments?.getLong("habitId"))
            val model: HabitDetailViewModel = viewModel(
                factory = DetailFactory(container, habitId),
            )
            HabitDetailScreen(
                viewModel = model,
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(HabitDestination.HabitEditor.route(id))
                },
                onArchived = { navController.returnToHabits() },
                onDeleted = { navController.returnToHabits() },
            )
        }
        composable(HabitDestination.Settings.route) {
            SettingsScreen(
                viewModel = viewModel(factory = SettingsFactory(container)),
                onCategories = {
                    navController.navigate(HabitDestination.Categories.route)
                },
                onOpenDrawer = onOpenDrawer,
            )
        }
        composable(HabitDestination.Categories.route) {
            CategoryScreen(
                viewModel = viewModel(factory = CategoryFactory(container)),
                onBack = onOpenDrawer,
            )
        }
    }
}

private fun NavHostController.completeOnboarding() {
    navigate(HabitDestination.Workbench.route) {
        popUpTo(HabitDestination.Welcome.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

private fun NavHostController.returnToHabits() {
    navigate(HabitDestination.Habits.route) {
        popUpTo(HabitDestination.Habits.route) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private class ListFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HabitListViewModel(
            container.habitRepository,
            container.categoryRepository,
        ) as T
}

private class EditorFactory(
    private val container: AppContainer,
    private val habitId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HabitEditorViewModel(
            container.habitRepository,
            container.categoryRepository,
            habitId,
        ) as T
}

private class DetailFactory(
    private val container: AppContainer,
    private val habitId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HabitDetailViewModel(
            habitId = habitId,
            calendarRepository = container.calendarRepository,
            habitRepository = container.habitRepository,
            categoryRepository = container.categoryRepository,
            dateProvider = container.dateProvider,
        ) as T
}

private class CategoryFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CategoryViewModel(
            container.categoryRepository,
            container.habitRepository,
        ) as T
}

private class CalendarFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CalendarViewModel(
            container.calendarRepository,
            container.checkInRepository,
            container.dateProvider,
        ) as T
}

private class SettingsFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(container.themeRepository) as T
}
