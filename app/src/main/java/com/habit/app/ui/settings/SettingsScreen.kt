package com.habit.app.ui.settings

import com.habit.app.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.data.backup.ImportMode
import com.habit.app.domain.time.HabitTimePolicy
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showReplaceConfirmation by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.changeBackupFolder(it.toString()) }
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.loadImport(it.toString()) }
    }

    Scaffold(
        topBar = {
            HabitTopAppBar(
                title = "主题与设置",
                navigationMode = NavigationMode.MENU,
                onNavigation = onOpenDrawer,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_screen")
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        Text("外观", style = MaterialTheme.typography.titleLarge)
        ThemePicker(
            selectedTheme = state.selectedTheme,
            onThemeSelected = viewModel::selectTheme,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().testTag("theme_preview"),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("主题预览", style = MaterialTheme.typography.titleMedium)
                Text("强调色会用于按钮、选中状态和进度。")
            }
        }
        Text("本地数据", style = MaterialTheme.typography.titleLarge)
        Card(
            modifier = Modifier.fillMaxWidth().testTag("backup_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("备份保存位置", style = MaterialTheme.typography.titleMedium)
                Text(state.backupLocation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "更换位置时会先复制并校验已有备份，成功后再切换；失败不会删除旧文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("change_backup_folder"),
                ) { Text("选择保存位置") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = viewModel::exportData,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_data"),
                    ) { Text("导出数据") }
                    OutlinedButton(
                        onClick = {
                            importPicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                        },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_data"),
                    ) { Text("导入数据") }
                }
                if (state.busy) Text("正在处理，请稍候…", color = MaterialTheme.colorScheme.primary)
            }
        }
        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings_message"),
            )
        }
        Text("版本", style = MaterialTheme.typography.titleLarge)
        Text("${BuildConfig.VERSION_NAME} · 内测版")
        }
    }

    state.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text("确认导入备份") },
            text = {
                Text(
                    "备份时间：${formatBackupTime(preview.backup.exportedAt)}\n" +
                        "${preview.categories} 个分类 · ${preview.habits} 个习惯 · ${preview.checkIns} 条打卡\n" +
                        "${preview.mealRecords} 条饮食 · ${preview.photos} 张照片" +
                        (if (preview.skippedPhotoCount > 0) "（缺失 ${preview.skippedPhotoCount} 张）\n" else "\n") +
                        state.importPreviewAiSummary.orEmpty() + "\n\n" +
                        "合并导入会保留双方数据；发生冲突时使用更新时间较新的数据。",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport(ImportMode.MERGE) }) { Text("合并导入") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showReplaceConfirmation = true }) { Text("完全替换") }
                    TextButton(onClick = viewModel::dismissImport) { Text("取消") }
                }
            },
        )
    }

    if (showReplaceConfirmation && state.importPreview != null) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmation = false },
            title = { Text("完全替换本机数据？") },
            text = { Text("此操作会用备份覆盖当前分类、习惯、打卡和偏好设置。建议先导出当前数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplaceConfirmation = false
                        viewModel.confirmImport(ImportMode.REPLACE)
                    },
                ) { Text("确认替换", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmation = false }) { Text("返回") }
            },
        )
    }
}

internal fun formatBackupTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(HabitTimePolicy.zoneId).format(BACKUP_TIME_FORMAT) + "（北京时间）"

private val BACKUP_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
