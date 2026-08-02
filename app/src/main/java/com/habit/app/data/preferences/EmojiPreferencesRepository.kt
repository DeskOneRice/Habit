package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val RECENT_SEPARATOR = "\u001F"
private val recentEmojiKey = stringPreferencesKey("recent_emoji_keys")

class EmojiPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val recentEmojiKeys: Flow<List<String>> = dataStore.data.map { preferences ->
        decodeRecent(preferences[recentEmojiKey])
    }

    suspend fun record(key: String) {
        dataStore.edit { preferences ->
            val updated = updatedRecentEmojiKeys(key, decodeRecent(preferences[recentEmojiKey]))
            preferences[recentEmojiKey] = updated.joinToString(RECENT_SEPARATOR)
        }
    }
}

fun updatedRecentEmojiKeys(selected: String, current: List<String>): List<String> =
    (listOf(selected) + current.filterNot { it == selected }).take(12)

private fun decodeRecent(raw: String?): List<String> = raw.orEmpty()
    .split(RECENT_SEPARATOR)
    .filter(String::isNotBlank)
    .distinct()
    .take(12)
