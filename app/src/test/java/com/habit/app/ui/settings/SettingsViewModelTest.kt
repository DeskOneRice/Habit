package com.habit.app.ui.settings

import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.ThemeRepository
import com.habit.app.data.backup.BackupOperations
import com.habit.app.data.backup.ExportResult
import com.habit.app.data.backup.FolderChangeResult
import com.habit.app.data.backup.ImportMode
import com.habit.app.data.backup.ImportPreview
import com.habit.app.data.backup.ImportResult
import com.habit.app.data.preferences.DEFAULT_BACKUP_LABEL
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

    @Test
    fun exportSuccessShowsFileNameAndDefaultLocation() = runTest(dispatcher) {
        val backup = RecordingBackupOperations()
        val viewModel = SettingsViewModel(RecordingThemeRepository(null), backup)

        viewModel.exportData()
        advanceUntilIdle()

        assertEquals(DEFAULT_BACKUP_LABEL, viewModel.state.value.backupLocation)
        assertEquals("已导出：Habit-Backup-test.habitbackup.json", viewModel.state.value.message)
    }

    @Test
    fun failedFolderMigrationKeepsOldLocation() = runTest(dispatcher) {
        val backup = RecordingBackupOperations(folderFailure = true)
        val viewModel = SettingsViewModel(RecordingThemeRepository(null), backup)

        viewModel.changeBackupFolder("content://new")
        advanceUntilIdle()

        assertEquals(DEFAULT_BACKUP_LABEL, viewModel.state.value.backupLocation)
        assertEquals("迁移失败，旧备份未删除", viewModel.state.value.message)
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

private class RecordingBackupOperations(
    private val folderFailure: Boolean = false,
) : BackupOperations {
    override val backupLocation = MutableStateFlow(DEFAULT_BACKUP_LABEL)

    override suspend fun export(): ExportResult =
        ExportResult("Habit-Backup-test.habitbackup.json", 1, 1, 1)

    override suspend fun preview(uri: String): ImportPreview = error("not used")

    override suspend fun import(preview: ImportPreview, mode: ImportMode): ImportResult = error("not used")

    override suspend fun changeFolder(uri: String): FolderChangeResult =
        if (folderFailure) FolderChangeResult.Failed("迁移失败，旧备份未删除")
        else FolderChangeResult.Success("新目录", 0)
}
