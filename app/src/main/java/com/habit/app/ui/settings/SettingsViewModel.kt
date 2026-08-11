package com.habit.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.data.backup.BackupOperations
import com.habit.app.data.backup.FolderChangeResult
import com.habit.app.data.backup.ImportMode
import com.habit.app.data.backup.ImportPreview
import com.habit.app.data.preferences.DEFAULT_BACKUP_LABEL
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedTheme: HabitThemeId = HabitThemeId.SKY_BLUE,
    val backupLocation: String = DEFAULT_BACKUP_LABEL,
    val busy: Boolean = false,
    val importPreview: ImportPreview? = null,
    val importPreviewAiSummary: String? = null,
    val message: String? = null,
)

class SettingsViewModel(
    private val themeRepository: ThemeRepository,
    private val backupOperations: BackupOperations? = null,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val importPreview = MutableStateFlow<ImportPreview?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        themeRepository.theme,
        backupOperations?.backupLocation ?: flowOf(DEFAULT_BACKUP_LABEL),
        busy,
        importPreview,
        message,
    ) { theme, backupLocation, currentBusy, preview, currentMessage ->
        SettingsUiState(
            selectedTheme = theme,
            backupLocation = backupLocation,
            busy = currentBusy,
            importPreview = preview,
            importPreviewAiSummary = preview?.let(::formatAiBackupPreview),
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    fun selectTheme(theme: HabitThemeId) {
        viewModelScope.launch {
            try {
                themeRepository.setTheme(theme)
                message.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "主题已应用，但可能无法在重启后保留"
            }
        }
    }

    fun exportData() = runBackupOperation {
        val result = requireBackupOperations().export()
        message.value = "已导出：${result.fileName}"
    }

    fun loadImport(uri: String) = runBackupOperation {
        importPreview.value = requireBackupOperations().preview(uri)
        message.value = null
    }

    fun dismissImport() {
        importPreview.value = null
    }

    fun confirmImport(mode: ImportMode) = runBackupOperation {
        val preview = importPreview.value ?: return@runBackupOperation
        val result = requireBackupOperations().import(preview, mode)
        importPreview.value = null
        val photoMessage = if (result.importedPhotoCount > 0 || result.skippedPhotoCount > 0) {
            "，${result.importedPhotoCount} 张照片${if (result.skippedPhotoCount > 0) "（跳过 ${result.skippedPhotoCount} 张无效照片）" else ""}"
        } else ""
        message.value = "导入完成：${result.summary.habits} 个习惯，${result.summary.checkIns} 条打卡$photoMessage"
    }

    fun changeBackupFolder(uri: String) = runBackupOperation {
        when (val result = requireBackupOperations().changeFolder(uri)) {
            is FolderChangeResult.Success -> message.value = "已迁移 ${result.migratedFiles} 个备份文件"
            is FolderChangeResult.Failed -> message.value = result.message
        }
    }

    private fun runBackupOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = error.message ?: "操作失败，请重试"
            } finally {
                busy.value = false
            }
        }
    }

    private fun requireBackupOperations(): BackupOperations =
        checkNotNull(backupOperations) { "备份服务尚未初始化" }
}

internal fun formatAiBackupPreview(preview: ImportPreview): String =
    "${preview.aiModelConfigs} 个模型配置 · ${preview.aiWeeklyReports} 份 AI 周报 · " +
        "${preview.aiCalorieEstimates} 条热量依据\nAPI Key 不包含在备份中"
