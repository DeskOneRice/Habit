package com.habit.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.habit.app.BuildConfig
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.data.ai.AndroidKeystoreAiSecretStore
import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.CalorieEstimateImagePreparer
import com.habit.app.data.ai.OpenAiCompatibleClient
import com.habit.app.data.ai.UrlConnectionAiHttpTransport
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.PresetCategoryCallback
import com.habit.app.data.local.MIGRATION_1_2
import com.habit.app.data.local.MIGRATION_2_3
import com.habit.app.data.local.MIGRATION_3_4
import com.habit.app.data.local.MIGRATION_4_5
import com.habit.app.data.backup.AndroidBackupDocumentStore
import com.habit.app.data.backup.BackupFolderMigrator
import com.habit.app.data.backup.HabitBackupService
import com.habit.app.data.backup.RoomBackupRepository
import com.habit.app.data.preferences.ThemePreferencesRepository
import com.habit.app.data.preferences.EmojiPreferencesRepository
import com.habit.app.data.preferences.BackupPreferencesRepository
import com.habit.app.data.preferences.DietPreferencesRepository
import com.habit.app.data.preferences.OnboardingPreferencesRepository
import com.habit.app.data.repository.RoomCalendarRepository
import com.habit.app.data.repository.RoomCategoryRepository
import com.habit.app.data.repository.RoomCheckInRepository
import com.habit.app.data.repository.RoomHabitRepository
import com.habit.app.data.repository.RoomDietRepository
import com.habit.app.data.repository.RoomDietTemplateRepository
import com.habit.app.data.repository.RoomDietCategoryRepository
import com.habit.app.data.repository.RoomAiModelRepository
import com.habit.app.data.repository.RoomAiWeeklyReportRepository
import com.habit.app.data.photos.AndroidDietPhotoStore
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.CheckInRepository
import com.habit.app.domain.repository.HabitRepository
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.repository.DietTemplateRepository
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.AiModelRepository
import com.habit.app.domain.repository.AiWeeklyReportRepository
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.ai.WeeklyReportInputBuilder
import com.habit.app.domain.time.DeviceDateProvider
import com.habit.app.domain.time.SystemDeviceDateProvider
import com.habit.app.ui.ai.AiModelOperationCoordinator
import com.habit.app.ui.ai.AiWeeklyReportViewModel
import com.habit.app.ui.workbench.WeeklyInsightSummary
import com.habit.app.ui.workbench.WeeklyInsightSavedData
import com.habit.app.ui.workbench.buildWeeklyInsightSummary
import java.time.Clock
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private const val THEME_PREFERENCES_FILE = "habit_theme_preferences"

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = THEME_PREFERENCES_FILE,
)

class AppContainer(
    context: Context,
    val dateProvider: DeviceDateProvider = SystemDeviceDateProvider(),
) {
    private val applicationContext = context.applicationContext
    private val clock: Clock = Clock.systemUTC()

    val database: HabitDatabase = Room.databaseBuilder(
        applicationContext,
        HabitDatabase::class.java,
        "habit.db",
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).addCallback(PresetCategoryCallback(clock)).build()

    val habitRepository: HabitRepository = RoomHabitRepository(database.habitDao(), clock)
    val categoryRepository: CategoryRepository = RoomCategoryRepository(database, clock)
    val checkInRepository: CheckInRepository = RoomCheckInRepository(database, clock)
    val calendarRepository: CalendarRepository = RoomCalendarRepository(
        database.habitDao(),
        database.checkInDao(),
    )
    val dietRepository: DietRepository = RoomDietRepository(database, clock)
    val dietCategoryRepository: DietCategoryRepository = RoomDietCategoryRepository(database, clock)
    val dietPhotoStore = AndroidDietPhotoStore(applicationContext)
    val dietTemplateRepository: DietTemplateRepository = RoomDietTemplateRepository(
        database,
        dietPhotoStore,
        clock,
    )
    val aiModelRepository: AiModelRepository = RoomAiModelRepository(database, clock)
    val aiWeeklyReportRepository: AiWeeklyReportRepository = RoomAiWeeklyReportRepository(database, clock)
    val aiSecretStore: AiSecretStore = AndroidKeystoreAiSecretStore(applicationContext)
    val aiCompletionClient: AiCompletionClient = OpenAiCompatibleClient(UrlConnectionAiHttpTransport())
    val calorieEstimateImagePreparer = CalorieEstimateImagePreparer(
        temporaryDirectory = File(applicationContext.cacheDir, "ai-calorie-images"),
    )
    internal val aiModelOperationCoordinator = AiModelOperationCoordinator()
    val weeklyReportInputBuilder = WeeklyReportInputBuilder(
        calendarRepository = calendarRepository,
        categoryRepository = categoryRepository,
        dietRepository = dietRepository,
    )
    val weeklyInsightSummary: Flow<WeeklyInsightSummary> = combine(
        aiModelRepository.observeModels(),
        aiModelRepository.observeBindings(),
        aiWeeklyReportRepository.observeAll(),
    ) { models, bindings, reports ->
        val boundId = bindings.firstOrNull { it.feature == AiFeature.WEEKLY_REPORT }?.modelConfigId
        buildWeeklyInsightSummary(
            now = clock.instant(),
            hasUsableWeeklyModel = models.firstOrNull { it.id == boundId }?.isUsableWeeklyModel() == true,
            savedReports = reports.map { report ->
                WeeklyInsightSavedData(
                    startEpochDay = report.startEpochDay,
                    endEpochDay = report.endEpochDay,
                    title = report.title,
                    overview = report.overview,
                    generatedAt = report.generatedAt,
                )
            },
        )
    }
    val themeRepository = ThemePreferencesRepository(applicationContext.themeDataStore, clock)
    val emojiPreferencesRepository = EmojiPreferencesRepository(applicationContext.themeDataStore, clock)
    val backupPreferencesRepository = BackupPreferencesRepository(applicationContext.themeDataStore)
    val dietPreferencesRepository = DietPreferencesRepository(applicationContext.themeDataStore, clock)
    val onboardingPreferencesRepository = OnboardingPreferencesRepository(applicationContext.themeDataStore)

    fun createAiWeeklyReportViewModel(): AiWeeklyReportViewModel = AiWeeklyReportViewModel(
        inputLoader = weeklyReportInputBuilder,
        modelRepository = aiModelRepository,
        reportRepository = aiWeeklyReportRepository,
        secretStore = aiSecretStore,
        client = aiCompletionClient,
        dateProvider = dateProvider,
        clock = clock,
        coordinator = aiModelOperationCoordinator,
    )
    private val backupDocumentStore = AndroidBackupDocumentStore(applicationContext)
    private val roomBackupRepository = RoomBackupRepository(database)
    val backupOperations = HabitBackupService(
        context = applicationContext,
        roomRepository = roomBackupRepository,
        preferencesRepository = backupPreferencesRepository,
        documentStore = backupDocumentStore,
        folderMigrator = BackupFolderMigrator(backupDocumentStore),
        photoStore = dietPhotoStore,
        clock = clock,
        appVersion = BuildConfig.VERSION_NAME,
    )
}

private fun AiModelConfig.isUsableWeeklyModel(): Boolean {
    if (!enabled || !supportsText) return false
    val textStatus = if (lastTestMessage.startsWith(TEST_STATE_PREFIX)) {
        lastTestMessage.removePrefix(TEST_STATE_PREFIX)
            .split('|')
            .firstOrNull { it.startsWith("text=") }
            ?.substringAfter('=')
            ?.let { runCatching { AiTestStatus.valueOf(it) }.getOrNull() }
            ?: AiTestStatus.UNTESTED
    } else {
        lastTestStatus
    }
    return textStatus == AiTestStatus.PASSED
}

private const val TEST_STATE_PREFIX = "habit-test-v1|"
