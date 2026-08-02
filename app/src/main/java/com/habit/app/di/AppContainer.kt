package com.habit.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.PresetCategoryCallback
import com.habit.app.data.backup.AndroidBackupDocumentStore
import com.habit.app.data.backup.BackupFolderMigrator
import com.habit.app.data.backup.HabitBackupService
import com.habit.app.data.backup.RoomBackupRepository
import com.habit.app.data.preferences.ThemePreferencesRepository
import com.habit.app.data.preferences.EmojiPreferencesRepository
import com.habit.app.data.preferences.BackupPreferencesRepository
import com.habit.app.data.repository.RoomCalendarRepository
import com.habit.app.data.repository.RoomCategoryRepository
import com.habit.app.data.repository.RoomCheckInRepository
import com.habit.app.data.repository.RoomHabitRepository
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.CheckInRepository
import com.habit.app.domain.repository.HabitRepository
import com.habit.app.domain.time.DeviceDateProvider
import com.habit.app.domain.time.SystemDeviceDateProvider
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
    ).addCallback(PresetCategoryCallback(clock)).build()

    val habitRepository: HabitRepository = RoomHabitRepository(database.habitDao(), clock)
    val categoryRepository: CategoryRepository = RoomCategoryRepository(database, clock)
    val checkInRepository: CheckInRepository = RoomCheckInRepository(database, clock)
    val calendarRepository: CalendarRepository = RoomCalendarRepository(
        database.habitDao(),
        database.checkInDao(),
    )
    val themeRepository = ThemePreferencesRepository(applicationContext.themeDataStore, clock)
    val emojiPreferencesRepository = EmojiPreferencesRepository(applicationContext.themeDataStore, clock)
    val backupPreferencesRepository = BackupPreferencesRepository(applicationContext.themeDataStore)
    private val backupDocumentStore = AndroidBackupDocumentStore(applicationContext)
    private val roomBackupRepository = RoomBackupRepository(database)
    val backupOperations = HabitBackupService(
        context = applicationContext,
        roomRepository = roomBackupRepository,
        preferencesRepository = backupPreferencesRepository,
        documentStore = backupDocumentStore,
        folderMigrator = BackupFolderMigrator(backupDocumentStore),
        clock = clock,
    )
}
