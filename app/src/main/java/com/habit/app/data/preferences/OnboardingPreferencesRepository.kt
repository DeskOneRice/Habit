package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OnboardingPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val completed: Flow<Boolean> = dataStore.data.map { values ->
        values[onboardingCompletedKey] ?: false
    }

    suspend fun complete() {
        dataStore.edit { values -> values[onboardingCompletedKey] = true }
    }
}
