package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ThemePreferencesRepository(private val dataStore: DataStore<Preferences>) : ThemeRepository {
    private val themeKey = stringPreferencesKey("theme_id")
    private val sessionTheme = MutableStateFlow<HabitThemeId?>(null)

    private val persistedTheme: Flow<HabitThemeId> = dataStore.data
        .catch { failure ->
            if (failure is CancellationException) throw failure
            emit(emptyPreferences())
        }
        .map { HabitThemeId.fromStored(it[themeKey]) }
        .distinctUntilChanged()

    override val theme: Flow<HabitThemeId> = combine(
        persistedTheme,
        sessionTheme,
    ) { persisted, selectedForSession ->
        selectedForSession ?: persisted
    }.distinctUntilChanged()

    override suspend fun setTheme(theme: HabitThemeId) {
        sessionTheme.value = theme
        dataStore.edit { it[themeKey] = theme.name }
    }
}
