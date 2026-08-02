package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.habit.app.data.backup.BackupPreferences
import com.habit.app.ui.theme.HabitThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

const val DEFAULT_BACKUP_LABEL = "内部存储 / Download / Habit / 数据备份"

sealed interface BackupFolder {
    data object Default : BackupFolder
    data class Tree(val uri: String, val displayName: String) : BackupFolder
}

data class TimestampedBackupPreferences(
    val preferences: BackupPreferences,
    val updatedAt: Long,
)

data class BackupPreferenceSnapshot(
    val content: TimestampedBackupPreferences,
    val folder: BackupFolder,
)

fun choosePreferences(
    current: TimestampedBackupPreferences,
    imported: TimestampedBackupPreferences,
): TimestampedBackupPreferences = if (imported.updatedAt > current.updatedAt) imported else current

class BackupPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    val snapshot: Flow<BackupPreferenceSnapshot> = dataStore.data.map { values ->
        val uri = values[backupFolderUriKey]
        BackupPreferenceSnapshot(
            content = TimestampedBackupPreferences(
                preferences = BackupPreferences(
                    themeId = HabitThemeId.fromStored(values[themeKey]).name.lowercase(),
                    recentEmojiKeys = decodeRecent(values[recentEmojiKey]),
                    dailyCalorieGoalEnabled = values[dailyCalorieGoalEnabledKey] ?: false,
                    dailyCalorieGoalKcal = values[dailyCalorieGoalKcalKey],
                ),
                updatedAt = values[preferencesUpdatedAtKey] ?: 0L,
            ),
            folder = if (uri.isNullOrBlank()) {
                BackupFolder.Default
            } else {
                BackupFolder.Tree(uri, values[backupFolderNameKey] ?: uri)
            },
        )
    }

    suspend fun current(): BackupPreferenceSnapshot = snapshot.first()

    suspend fun restore(content: TimestampedBackupPreferences) {
        dataStore.edit { values ->
            values[themeKey] = content.preferences.themeId.uppercase()
            values[recentEmojiKey] = encodeRecent(content.preferences.recentEmojiKeys)
            values[dailyCalorieGoalEnabledKey] = content.preferences.dailyCalorieGoalEnabled
            content.preferences.dailyCalorieGoalKcal?.let { values[dailyCalorieGoalKcalKey] = it }
                ?: values.remove(dailyCalorieGoalKcalKey)
            values[preferencesUpdatedAtKey] = content.updatedAt
        }
    }

    suspend fun setFolder(folder: BackupFolder) {
        dataStore.edit { values ->
            when (folder) {
                BackupFolder.Default -> {
                    values.remove(backupFolderUriKey)
                    values.remove(backupFolderNameKey)
                }
                is BackupFolder.Tree -> {
                    values[backupFolderUriKey] = folder.uri
                    values[backupFolderNameKey] = folder.displayName
                }
            }
        }
    }
}
