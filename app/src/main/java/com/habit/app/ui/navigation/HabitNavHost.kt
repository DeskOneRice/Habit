package com.habit.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.habit.app.ui.habits.HabitEditorScreen
import com.habit.app.ui.habits.HabitEditorViewModel
import com.habit.app.ui.habits.HabitListScreen
import com.habit.app.ui.habits.HabitListViewModel
import com.habit.app.ui.welcome.WelcomeScreen

@Composable fun HabitNavHost(navController: NavHostController, startDestination: HabitDestination, container: AppContainer) {
    NavHost(navController, startDestination.route) {
        composable(HabitDestination.Welcome.route) { WelcomeScreen { navController.navigate(HabitDestination.HabitEditor.route()) } }
        composable(HabitDestination.Calendar.route) {
            CalendarScreen(viewModel(factory = CalendarFactory(container)))
        }
        composable(HabitDestination.Habits.route) { HabitListScreen(viewModel(factory = ListFactory(container)), { navController.navigate(HabitDestination.HabitEditor.route()) }, { navController.navigate(HabitDestination.HabitEditor.route(it)) }, { navController.navigate(HabitDestination.Categories.route) }) }
        composable(HabitDestination.HabitEditor.route, arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val model: HabitEditorViewModel = viewModel(factory = EditorFactory(container, entry.arguments?.getLong("habitId")?.takeIf { it >= 0 }))
            HabitEditorScreen(model, container.categoryRepository, { navController.navigate(HabitDestination.Habits.route) { popUpTo(HabitDestination.Welcome.route) { inclusive = true } } }, { navController.popBackStack() }, { id -> model.archive(id) { navController.navigate(HabitDestination.Habits.route) } }, { id -> model.delete(id) { navController.navigate(HabitDestination.Habits.route) } })
        }
        composable(HabitDestination.HabitDetail.route) { DestinationPlaceholder("habit_detail_screen", "习惯详情") }
        composable(HabitDestination.Settings.route) { DestinationPlaceholder("settings_screen", "设置") }
        composable(HabitDestination.Categories.route) { CategoryScreen(viewModel(factory = CategoryFactory(container))) { navController.popBackStack() } }
    }
}
@Composable private fun DestinationPlaceholder(tag: String, title: String) = Column(Modifier.fillMaxSize().testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(title) }
private class ListFactory(private val c: AppContainer) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = HabitListViewModel(c.habitRepository, c.categoryRepository) as T }
private class EditorFactory(private val c: AppContainer, private val id: Long?) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = HabitEditorViewModel(c.habitRepository, c.categoryRepository, id) as T }
private class CategoryFactory(private val c: AppContainer) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = CategoryViewModel(c.categoryRepository, c.habitRepository) as T }
private class CalendarFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>) =
        CalendarViewModel(c.calendarRepository, c.checkInRepository, c.dateProvider) as T
}
