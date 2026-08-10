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
import com.habit.app.domain.time.DeviceDateProvider
import com.habit.app.domain.time.SystemDeviceDateProvider
import com.habit.app.ui.ai.AiModelOperationCoordinator
import java.time.Clock

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
    internal val aiModelOperationCoordinator = AiModelOperationCoordinator()
    val themeRepository = ThemePreferencesRepository(applicationContext.themeDataStore, clock)
    val emojiPreferencesRepository = EmojiPreferencesRepository(applicationContext.themeDataStore, clock)
    val backupPreferencesRepository = BackupPreferencesRepository(applicationContext.themeDataStore)
    val dietPreferencesRepository = DietPreferencesRepository(applicationContext.themeDataStore, clock)
    val onboardingPreferencesRepository = OnboardingPreferencesRepository(applicationContext.themeDataStore)
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
