package com.habit.app.data.backup

import com.habit.app.data.preferences.BackupFolder

private val managedBackupPattern = Regex("Habit-Backup-[A-Za-z0-9_-]+\\.habitbackup\\.json")

fun isManagedBackupName(name: String): Boolean = managedBackupPattern.matches(name)

interface BackupDocumentStore {
    suspend fun listManagedBackups(folder: BackupFolder): List<String>
    suspend fun read(folder: BackupFolder, name: String): ByteArray
    suspend fun write(folder: BackupFolder, name: String, bytes: ByteArray)
    suspend fun delete(folder: BackupFolder, name: String)
}
