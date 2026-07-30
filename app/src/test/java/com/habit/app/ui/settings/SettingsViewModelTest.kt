package com.habit.app.ui.settings

import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun writeFailureKeepsSessionSelectionAndShowsWarning() = runTest(dispatcher) {
        val repository = RecordingThemeRepository(IllegalStateException("disk full"))
        val viewModel = SettingsViewModel(repository)

        viewModel.selectTheme(HabitThemeId.SAGE_GREEN)
        advanceUntilIdle()

        assertEquals(HabitThemeId.SAGE_GREEN, repository.theme.value)
        assertEquals(HabitThemeId.SAGE_GREEN, viewModel.state.value.selectedTheme)
        assertEquals(
            "主题已应用，但可能无法在重启后保留",
            viewModel.state.value.message,
        )
    }

    @Test
    fun cancellationIsNotConvertedToPersistenceWarning() = runTest(dispatcher) {
        val repository = RecordingThemeRepository(CancellationException("cancel"))
        val viewModel = SettingsViewModel(repository)

        viewModel.selectTheme(HabitThemeId.MIST_PURPLE)
        advanceUntilIdle()

        assertEquals(HabitThemeId.MIST_PURPLE, repository.theme.value)
        assertNull(viewModel.state.value.message)
    }
}

private class RecordingThemeRepository(
    private val failure: Throwable?,
) : ThemeRepository {
    override val theme = MutableStateFlow(HabitThemeId.SKY_BLUE)

    override suspend fun setTheme(theme: HabitThemeId) {
        this.theme.value = theme
        failure?.let { throw it }
    }
}
