package com.habit.app.data.backup

import com.habit.app.data.preferences.BackupFolder
import java.security.MessageDigest

sealed interface FolderMigrationResult {
    data class Success(val migratedFiles: Int) : FolderMigrationResult
    data class Failed(val message: String) : FolderMigrationResult
}

class BackupFolderMigrator(private val store: BackupDocumentStore) {
    suspend fun migrate(oldFolder: BackupFolder, newFolder: BackupFolder): FolderMigrationResult {
        if (oldFolder == newFolder) return FolderMigrationResult.Success(0)
        val createdTargets = mutableListOf<String>()
        return try {
            val names = store.listManagedBackups(oldFolder)
            names.forEach { name ->
                val source = store.read(oldFolder, name)
                store.write(newFolder, name, source)
                createdTargets += name
                val target = store.read(newFolder, name)
                check(source.size == target.size && source.sha256().contentEquals(target.sha256())) {
                    "备份校验失败：$name"
                }
            }
            names.forEach { store.delete(oldFolder, it) }
            FolderMigrationResult.Success(names.size)
        } catch (error: Exception) {
            createdTargets.forEach { name -> runCatching { store.delete(newFolder, name) } }
            FolderMigrationResult.Failed(error.message ?: "备份目录迁移失败")
        }
    }
}

private fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)
