package com.habit.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
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
import com.habit.app.ui.categories.CategorySection
import com.habit.app.ui.components.NavigationMode
import com.habit.app.ui.habits.HabitDetailScreen
import com.habit.app.ui.habits.HabitDetailViewModel
import com.habit.app.ui.habits.HabitEditorScreen
import com.habit.app.ui.habits.HabitEditorViewModel
import com.habit.app.ui.habits.HabitListScreen
import com.habit.app.ui.habits.HabitListViewModel
import com.habit.app.ui.settings.SettingsScreen
import com.habit.app.ui.settings.SettingsViewModel
import com.habit.app.ui.diet.DietDiaryScreen
import com.habit.app.ui.diet.DietDiaryViewModel
import com.habit.app.ui.diet.DietRecordDetailScreen
import com.habit.app.ui.diet.DietRecordDetailViewModel
import com.habit.app.ui.diet.DietEditorScreen
import com.habit.app.ui.diet.DietEditorViewModel
import com.habit.app.ui.diet.DietTemplateScreen
import com.habit.app.ui.diet.DietTemplateViewModel
import com.habit.app.ui.diet.DietSettingsScreen
import com.habit.app.ui.diet.DietSettingsViewModel
import com.habit.app.ui.diet.DietStatsScreen
import com.habit.app.ui.diet.DietStatsViewModel
import com.habit.app.ui.welcome.WelcomeScreen
import com.habit.app.ui.workbench.WorkbenchScreen
import com.habit.app.ui.workbench.WorkbenchViewModel
import com.habit.app.ui.ai.AiModelEditorScreen
import com.habit.app.ui.ai.AiSettingsScreen
import com.habit.app.ui.ai.AiSettingsViewModel
import com.habit.app.ui.ai.AiWeeklyReportScreen
import com.habit.app.ui.ai.AiReportHistoryScreen
import com.habit.app.ui.ai.AiWeeklyReportDetailScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HabitNavHost(
    navController: NavHostController,
    startDestination: HabitDestination,
    container: AppContainer,
    workbenchViewModel: WorkbenchViewModel,
    onOpenDrawer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    NavHost(navController, startDestination.route) {
        composable(HabitDestination.Welcome.route) {
            WelcomeScreen(
                onCreateHabit = { navController.navigate(HabitDestination.HabitEditor.route()) },
                onSkip = {
                    scope.launch {
                        container.onboardingPreferencesRepository.complete()
                        withContext(Dispatchers.Main.immediate) {
                            navController.completeOnboarding()
                        }
                    }
                },
            )
        }
        composable(HabitDestination.Workbench.route) {
            WorkbenchScreen(
                viewModel = workbenchViewModel,
                onOpenDrawer = onOpenDrawer,
                onCreateHabit = { navController.navigate(HabitDestination.HabitEditor.route()) },
                onOpenCalendar = { navController.navigate(HabitDestination.Calendar.route) },
                onOpenHabit = { navController.navigate(HabitDestination.HabitDetail.route(it)) },
                onOpenDiet = { navController.navigate(HabitDestination.DietDiary.route) },
                onAddDiet = { navController.navigate(HabitDestination.DietEditor.route()) },
                onUseDietTemplate = { navController.navigate(HabitDestination.DietEditor.templateRoute(it)) },
                onOpenWeeklyReport = { startEpochDay ->
                    navController.navigate(
                        startEpochDay?.let(HabitDestination.AiReportDetail::route)
                            ?: HabitDestination.AiReports.route,
                    )
                },
                onOpenAiSettings = { navController.navigate(HabitDestination.AiSettings.route) },
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
                            scope.launch {
                                container.onboardingPreferencesRepository.complete()
                                withContext(Dispatchers.Main.immediate) {
                                    navController.completeOnboarding()
                                }
                            }
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
                onManageCategories = {
                    navController.navigate(HabitDestination.Categories.route(CategorySection.HABIT.name))
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
                onOpenDrawer = onOpenDrawer,
            )
        }
        composable(
            route = HabitDestination.Categories.route,
            arguments = listOf(navArgument("section") {
                type = NavType.StringType
                defaultValue = CategorySection.HABIT.name
            }),
        ) { entry ->
            val initialSection = entry.arguments?.getString("section")
                ?.let { runCatching { CategorySection.valueOf(it) }.getOrNull() }
                ?: CategorySection.HABIT
            CategoryScreen(
                viewModel = viewModel(factory = CategoryFactory(container, initialSection)),
                navigationMode = NavigationMode.BACK,
                onNavigation = { navController.popBackStack() },
            )
        }
        composable(HabitDestination.DrawerCategories.route) {
            CategoryScreen(
                viewModel = viewModel(factory = CategoryFactory(container)),
                navigationMode = NavigationMode.MENU,
                onNavigation = onOpenDrawer,
            )
        }
        composable(HabitDestination.DietDiary.route) {
            DietDiaryScreen(
                viewModel = viewModel(factory = DietDiaryFactory(container)),
                photoStore = container.dietPhotoStore,
                onOpenDrawer = onOpenDrawer,
                onAdd = { navController.navigate(HabitDestination.DietEditor.route()) },
                onOpenRecord = { navController.navigate(HabitDestination.DietDetail.route(it)) },
                onRepeatRecord = { navController.navigate(HabitDestination.DietEditor.repeatRoute(it)) },
            )
        }
        composable(
            route = HabitDestination.DietDetail.route,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
        ) { entry ->
            val recordId = requireNotNull(entry.arguments?.getLong("recordId"))
            DietRecordDetailScreen(
                viewModel = viewModel(factory = DietRecordDetailFactory(container, recordId)),
                photoStore = container.dietPhotoStore,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(HabitDestination.DietEditor.route(it)) },
                onRepeat = { navController.navigate(HabitDestination.DietEditor.repeatRoute(it)) },
            )
        }
        composable(HabitDestination.DietTemplates.route) {
            DietTemplateScreen(
                viewModel = viewModel(factory = DietTemplateFactory(container)),
                onOpenDrawer = onOpenDrawer,
                onUseTemplate = { navController.navigate(HabitDestination.DietEditor.templateRoute(it)) },
            )
        }
        composable(
            route = HabitDestination.DietEditor.route,
            arguments = listOf(
                navArgument("recordId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("repeatId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("templateId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val recordId = entry.arguments?.getLong("recordId")?.takeIf { it >= 0 }
            val repeatId = entry.arguments?.getLong("repeatId")?.takeIf { it >= 0 }
            val templateId = entry.arguments?.getLong("templateId")?.takeIf { it >= 0 }
            DietEditorScreen(
                viewModel = viewModel(factory = DietEditorFactory(container, recordId, repeatId, templateId)),
                isEditing = recordId != null,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onManageCategories = { recordType ->
                    val section = if (recordType == com.habit.app.domain.model.DietRecordType.BEVERAGE) {
                        CategorySection.BEVERAGE
                    } else {
                        CategorySection.MEAL
                    }
                    navController.navigate(HabitDestination.Categories.route(section.name))
                },
            )
        }
        composable(HabitDestination.DietStats.route) {
            DietStatsScreen(viewModel(factory = DietStatsFactory(container)), onOpenDrawer)
        }
        composable(HabitDestination.DietSettings.route) {
            DietSettingsScreen(viewModel(factory = DietSettingsFactory(container)), onOpenDrawer)
        }
        composable(HabitDestination.AiReports.route) {
            AiWeeklyReportScreen(
                viewModel = viewModel(factory = AiWeeklyReportFactory(container)),
                navigationMode = NavigationMode.MENU,
                onNavigation = onOpenDrawer,
                onOpenHistory = { navController.navigate(HabitDestination.AiReportHistory.route) },
                onOpenSavedReport = {
                    navController.navigate(HabitDestination.AiReportDetail.route(it)) { launchSingleTop = true }
                },
                onOpenModelSettings = { navController.navigate(HabitDestination.AiSettings.route) },
            )
        }
        composable(HabitDestination.AiReportHistory.route) {
            val reports by container.aiWeeklyReportRepository.observeAll().collectAsStateWithLifecycle(emptyList())
            AiReportHistoryScreen(
                reports = reports,
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(HabitDestination.AiReportDetail.route(it)) },
            )
        }
        composable(
            route = HabitDestination.AiReportDetail.route,
            arguments = listOf(navArgument("startEpochDay") { type = NavType.LongType }),
        ) { entry ->
            val startEpochDay = requireNotNull(entry.arguments?.getLong("startEpochDay"))
            val report by container.aiWeeklyReportRepository.observeWeek(startEpochDay)
                .collectAsStateWithLifecycle(initialValue = null)
            report?.let {
                AiWeeklyReportDetailScreen(it) { navController.popBackStack() }
            } ?: Text("周报不存在")
        }
        composable(HabitDestination.AiSettings.route) {
            AiSettingsScreen(
                viewModel = viewModel(factory = AiSettingsFactory(container)),
                onOpenDrawer = onOpenDrawer,
                onAddModel = { navController.navigate(HabitDestination.AiModelEditor.route()) },
                onEditModel = { navController.navigate(HabitDestination.AiModelEditor.route(it)) },
            )
        }
        composable(
            route = HabitDestination.AiModelEditor.route,
            arguments = listOf(navArgument("modelId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val modelId = entry.arguments?.getLong("modelId")?.takeIf { it >= 0 }
            AiModelEditorScreen(
                viewModel = viewModel(factory = AiSettingsFactory(container)),
                modelId = modelId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
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
    private val initialSection: CategorySection = CategorySection.HABIT,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CategoryViewModel(
            container.categoryRepository,
            container.habitRepository,
            container.dietCategoryRepository,
            initialSection,
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
        SettingsViewModel(container.themeRepository, container.backupOperations) as T
}

private class DietDiaryFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DietDiaryViewModel(
            container.dietRepository,
            container.dateProvider,
            container.dietCategoryRepository,
        ) as T
}

private class DietEditorFactory(
    private val container: AppContainer,
    private val recordId: Long?,
    private val repeatId: Long?,
    private val templateId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DietEditorViewModel(
            recordId,
            container.dietRepository,
            container.dateProvider,
            photoStore = container.dietPhotoStore,
            repeatRecordId = repeatId,
            templateId = templateId,
            templateRepository = container.dietTemplateRepository,
            dietCategoryRepository = container.dietCategoryRepository,
        ) as T
}

private class DietRecordDetailFactory(
    private val container: AppContainer,
    private val recordId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DietRecordDetailViewModel(
            recordId = recordId,
            repository = container.dietRepository,
            categoryRepository = container.dietCategoryRepository,
        ) as T
}

private class DietTemplateFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DietTemplateViewModel(container.dietTemplateRepository) as T
}

private class DietStatsFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DietStatsViewModel(
            container.dietRepository,
            container.dietCategoryRepository,
            container.dateProvider,
        ) as T
}

private class DietSettingsFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DietSettingsViewModel(container.dietPreferencesRepository) as T
}

private class AiSettingsFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AiSettingsViewModel(
            repository = container.aiModelRepository,
            secretStore = container.aiSecretStore,
            client = container.aiCompletionClient,
            coordinator = container.aiModelOperationCoordinator,
        ) as T
}

private class AiWeeklyReportFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        container.createAiWeeklyReportViewModel() as T
}
