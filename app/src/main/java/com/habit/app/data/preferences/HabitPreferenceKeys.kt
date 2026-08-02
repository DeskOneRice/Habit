package com.habit.app.data.preferences

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal const val RECENT_SEPARATOR = "\u001F"
internal val themeKey = stringPreferencesKey("theme_id")
internal val recentEmojiKey = stringPreferencesKey("recent_emoji_keys")
internal val preferencesUpdatedAtKey = longPreferencesKey("habit_preferences_updated_at")
internal val backupFolderUriKey = stringPreferencesKey("backup_folder_uri")
internal val backupFolderNameKey = stringPreferencesKey("backup_folder_name")

internal fun encodeRecent(values: List<String>): String = values.distinct().take(12).joinToString(RECENT_SEPARATOR)

internal fun decodeRecent(raw: String?): List<String> = raw.orEmpty()
    .split(RECENT_SEPARATOR)
    .filter(String::isNotBlank)
    .distinct()
    .take(12)
