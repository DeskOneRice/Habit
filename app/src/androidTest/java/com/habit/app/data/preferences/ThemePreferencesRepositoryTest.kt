package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.ui.theme.HabitThemeId
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
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

    @Test
    fun readFailureFallsBackToSkyBlue() = runTest {
        val repository = ThemePreferencesRepository(
            FailureInjectingDataStore(readFailure = IllegalStateException("read failed")),
        )

        assertEquals(HabitThemeId.SKY_BLUE, repository.theme.first())
    }

    @Test
    fun writeFailureRetainsSelectedThemeForSession() = runTest {
        val failure = IllegalStateException("write failed")
        val repository = ThemePreferencesRepository(
            FailureInjectingDataStore(writeFailure = failure),
        )

        val thrown = captureFailure {
            repository.setTheme(HabitThemeId.MIST_PURPLE)
        }

        assertSame(failure, thrown)
        assertEquals(HabitThemeId.MIST_PURPLE, repository.theme.first())
    }

    @Test
    fun readCancellationIsRethrown() = runTest {
        val cancellation = CancellationException("read cancelled")
        val repository = ThemePreferencesRepository(
            FailureInjectingDataStore(readFailure = cancellation),
        )

        assertSame(cancellation, captureFailure { repository.theme.first() })
    }

    @Test
    fun writeCancellationIsRethrownWhileSessionSelectionRemainsApplied() = runTest {
        val cancellation = CancellationException("write cancelled")
        val repository = ThemePreferencesRepository(
            FailureInjectingDataStore(writeFailure = cancellation),
        )

        assertSame(
            cancellation,
            captureFailure { repository.setTheme(HabitThemeId.SAGE_GREEN) },
        )
        assertEquals(HabitThemeId.SAGE_GREEN, repository.theme.first())
    }
}

private class FailureInjectingDataStore(
    private val readFailure: Throwable? = null,
    private val writeFailure: Throwable? = null,
) : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = if (readFailure == null) {
        state
    } else {
        flow { throw readFailure }
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        writeFailure?.let { throw it }
        return transform(state.value).also { state.value = it }
    }
}

private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
    try {
        block()
    } catch (failure: Throwable) {
        return failure
    }
    fail("Expected failure")
    error("unreachable")
}
