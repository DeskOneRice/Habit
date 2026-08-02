package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EmojiPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock = Clock.systemUTC(),
) {
    val recentEmojiKeys: Flow<List<String>> = dataStore.data.map { preferences ->
        decodeRecent(preferences[recentEmojiKey])
    }

    suspend fun record(key: String) {
        dataStore.edit { preferences ->
            val updated = updatedRecentEmojiKeys(key, decodeRecent(preferences[recentEmojiKey]))
            preferences[recentEmojiKey] = encodeRecent(updated)
            preferences[preferencesUpdatedAtKey] = clock.millis()
        }
    }
}

fun updatedRecentEmojiKeys(selected: String, current: List<String>): List<String> =
    (listOf(selected) + current.filterNot { it == selected }).take(12)
