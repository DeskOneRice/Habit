package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.habit.app.ui.theme.HabitThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ThemePreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme_id")

    val theme: Flow<HabitThemeId> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { HabitThemeId.fromStored(it[themeKey]) }
        .distinctUntilChanged()

    suspend fun setTheme(theme: HabitThemeId) {
        dataStore.edit { it[themeKey] = theme.name }
    }
}
