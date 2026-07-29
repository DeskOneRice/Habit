package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.ui.theme.HabitThemeId
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = TestScope(UnconfinedTestDispatcher()),
        produceFile = { File(temporaryFolder.root, "theme.preferences_pb") },
    )

    @Test
    fun missingAndUnknownValuesFallBackToSkyBlue() = runTest {
        val store = createStore()
        val repository = ThemePreferencesRepository(store)

        assertEquals(HabitThemeId.SKY_BLUE, repository.theme.first())

        store.edit { it[stringPreferencesKey("theme_id")] = "UNKNOWN" }

        assertEquals(HabitThemeId.SKY_BLUE, repository.theme.first())
    }

    @Test
    fun selectedThemeSurvivesRepositoryRecreation() = runTest {
        val store = createStore()
        ThemePreferencesRepository(store).setTheme(HabitThemeId.SAGE_GREEN)

        assertEquals(HabitThemeId.SAGE_GREEN, ThemePreferencesRepository(store).theme.first())
    }
}
