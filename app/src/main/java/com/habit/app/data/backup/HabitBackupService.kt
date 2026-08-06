package com.habit.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.habit.app.data.preferences.BackupFolder
import com.habit.app.data.preferences.BackupPreferencesRepository
import com.habit.app.data.preferences.DEFAULT_BACKUP_LABEL
import com.habit.app.data.preferences.TimestampedBackupPreferences
import com.habit.app.data.preferences.choosePreferences
import com.habit.app.domain.time.HabitTimePolicy
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.habit.app.data.photos.DietPhotoStore
import java.io.BufferedInputStream
import java.io.File
import java.util.UUID

data class ExportResult(
    val fileName: String,
    val categories: Int,
    val habits: Int,
    val checkIns: Int,
    val mealRecords: Int = 0,
)

data class ImportPreview(
    val backup: HabitBackup,
    val sourceUri: String? = null,
    val isArchive: Boolean = false,
    val skippedPhotoCount: Int = 0,
) {
    val categories: Int get() = backup.categories.size
    val habits: Int get() = backup.habits.size
    val checkIns: Int get() = backup.checkIns.size
    val mealRecords: Int get() = backup.mealRecords.size
    val photos: Int get() = backup.dietPhotos.size
}

data class ImportResult(
    val summary: ImportSummary,
    val importedPhotoCount: Int = 0,
    val skippedPhotoCount: Int = 0,
)

sealed interface FolderChangeResult {
    data class Success(val displayName: String, val migratedFiles: Int) : FolderChangeResult
    data class Failed(val message: String) : FolderChangeResult
}

interface BackupOperations {
    val backupLocation: Flow<String>
    suspend fun export(): ExportResult
    suspend fun preview(uri: String): ImportPreview
    suspend fun import(preview: ImportPreview, mode: ImportMode): ImportResult
    suspend fun changeFolder(uri: String): FolderChangeResult
}

class HabitBackupService(
    context: Context,
    private val roomRepository: RoomBackupRepository,
    private val preferencesRepository: BackupPreferencesRepository,
    private val documentStore: BackupDocumentStore,
    private val folderMigrator: BackupFolderMigrator,
    private val photoStore: DietPhotoStore,
    private val clock: Clock = Clock.systemUTC(),
    private val appVersion: String = "0.4.0",
) : BackupOperations {
    private val applicationContext = context.applicationContext
    private val resolver = applicationContext.contentResolver

    override val backupLocation: Flow<String> = preferencesRepository.snapshot
        .map { snapshot ->
            when (val folder = snapshot.folder) {
                BackupFolder.Default -> DEFAULT_BACKUP_LABEL
                is BackupFolder.Tree -> folder.displayName
            }
        }
        .distinctUntilChanged()

    override suspend fun export(): ExportResult {
        val preferenceSnapshot = preferencesRepository.current()
        val database = roomRepository.exportDatabase()
        val now = clock.millis()
        val backup = HabitBackup(
            appVersion = appVersion,
            exportedAt = now,
            preferencesUpdatedAt = preferenceSnapshot.content.updatedAt,
            categories = database.categories,
            habits = database.habits,
            checkIns = database.checkIns,
            dietCategories = database.dietCategories,
            mealRecords = database.mealRecords,
            foodItems = database.foodItems,
            beverageDetails = database.beverageDetails,
            beverageToppings = database.beverageToppings,
            dietPhotos = database.dietPhotos,
            dietTemplates = database.dietTemplates,
            dietTemplateFoodItems = database.dietTemplateFoodItems,
            dietTemplateToppings = database.dietTemplateToppings,
            preferences = preferenceSnapshot.content.preferences,
        )
        val fileName = backupFileName(now)
        documentStore.writeStream(
            preferenceSnapshot.folder,
            fileName,
            "application/zip",
        ) { output ->
            HabitBackupArchive.write(output, backup) { path ->
                runCatching { photoStore.file(path) }.getOrNull()?.takeIf(File::isFile)?.inputStream()
            }
        }
        return ExportResult(fileName, backup.categories.size, backup.habits.size, backup.checkIns.size, backup.mealRecords.size)
    }

    override suspend fun preview(uri: String): ImportPreview = withContext(Dispatchers.IO) {
        val source = resolver.openInputStream(Uri.parse(uri)) ?: error("无法读取所选备份")
        BufferedInputStream(source).use { input ->
            input.mark(4)
            val signature = ByteArray(4)
            val count = input.read(signature)
            input.reset()
            val isArchive = count >= 2 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte()
            if (isArchive) {
                val temp = File(applicationContext.cacheDir, "backup_preview_${UUID.randomUUID()}")
                try {
                    val archive = HabitBackupArchive.read(input, temp)
                    ImportPreview(archive.backup, uri, true, archive.skippedPhotoCount)
                } finally {
                    temp.deleteRecursively()
                }
            } else {
                ImportPreview(HabitBackupCodec.decode(input.bufferedReader().readText()), uri, false)
            }
        }
    }

    override suspend fun import(preview: ImportPreview, mode: ImportMode): ImportResult {
        val current = preferencesRepository.current().content
        var backupToImport = preview.backup
        val importedPaths = mutableListOf<String>()
        var skippedPhotos = if (preview.isArchive) 0 else preview.skippedPhotoCount
        if (preview.isArchive) {
            val uri = requireNotNull(preview.sourceUri) { "备份来源已失效" }
            val temp = File(applicationContext.cacheDir, "backup_import_${UUID.randomUUID()}")
            try {
                val archive = resolver.openInputStream(Uri.parse(uri))?.use { HabitBackupArchive.read(it, temp) }
                    ?: error("无法重新读取所选备份")
                val remapped = mutableListOf<BackupDietPhoto>()
                archive.backup.dietPhotos.forEach { photo ->
                    val source = archive.photos[photo.relativePath]
                    if (source == null) {
                        skippedPhotos += 1
                    } else {
                        val imported = runCatching { photoStore.importFile(source) }.getOrNull()
                        if (imported == null) skippedPhotos += 1 else {
                            importedPaths += imported.relativePath
                            remapped += photo.copy(relativePath = imported.relativePath)
                        }
                    }
                }
                backupToImport = archive.backup.copy(dietPhotos = remapped)
            } finally {
                temp.deleteRecursively()
            }
        }
        val imported = TimestampedBackupPreferences(
            backupToImport.preferences,
            backupToImport.preferencesUpdatedAt,
        )
        return try {
            val summary = roomRepository.importDatabase(backupToImport, mode)
            preferencesRepository.restore(
                if (mode == ImportMode.REPLACE) imported else choosePreferences(current, imported),
            )
            photoStore.removeOrphans(roomRepository.referencedPhotoPaths())
            ImportResult(summary, importedPaths.size, skippedPhotos)
        } catch (error: Exception) {
            importedPaths.forEach { photoStore.delete(it) }
            throw error
        }
    }

    override suspend fun changeFolder(uri: String): FolderChangeResult {
        val parsed = Uri.parse(uri)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            resolver.takePersistableUriPermission(parsed, flags)
            val displayName = DocumentFile.fromTreeUri(applicationContext, parsed)?.name
                ?: parsed.lastPathSegment
                ?: "自定义备份目录"
            val newFolder = BackupFolder.Tree(uri, displayName)
            val oldFolder = preferencesRepository.current().folder
            when (val migrated = folderMigrator.migrate(oldFolder, newFolder)) {
                is FolderMigrationResult.Success -> {
                    preferencesRepository.setFolder(newFolder)
                    if (oldFolder is BackupFolder.Tree && oldFolder.uri != uri) {
                        runCatching { resolver.releasePersistableUriPermission(Uri.parse(oldFolder.uri), flags) }
                    }
                    FolderChangeResult.Success(displayName, migrated.migratedFiles)
                }
                is FolderMigrationResult.Failed -> {
                    runCatching { resolver.releasePersistableUriPermission(parsed, flags) }
                    FolderChangeResult.Failed("迁移失败，旧备份未删除")
                }
            }
        } catch (error: Exception) {
            FolderChangeResult.Failed(error.message ?: "无法使用所选目录")
        }
    }

}

internal fun backupFileName(epochMillis: Long): String =
    "Habit-Backup-${BACKUP_FILE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))}.habitbackup.zip"

private val BACKUP_FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyyMMdd-HHmmss")
    .withZone(HabitTimePolicy.zoneId)
