package com.habit.app.data.backup

import com.habit.app.data.preferences.BackupFolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFolderMigratorTest {
    @Test
    fun oldFilesAreDeletedOnlyAfterEveryCopyMatches() = runTest {
        val store = FakeBackupDocumentStore().apply {
            put(BackupFolder.Default, "Habit-Backup-a.habitbackup.json", "one")
            put(BackupFolder.Default, "Habit-Backup-b.habitbackup.json", "two")
        }
        val target = BackupFolder.Tree("content://new", "新目录")

        val result = BackupFolderMigrator(store).migrate(BackupFolder.Default, target)

        assertEquals(FolderMigrationResult.Success(2), result)
        assertTrue(store.listManagedBackups(BackupFolder.Default).isEmpty())
        assertEquals(2, store.listManagedBackups(target).size)
    }

    @Test
    fun checksumFailureKeepsEveryOldFile() = runTest {
        val store = FakeBackupDocumentStore().apply {
            put(BackupFolder.Default, "Habit-Backup-a.habitbackup.json", "one")
            put(BackupFolder.Default, "Habit-Backup-b.habitbackup.json", "two")
            corruptNextWrite = true
        }
        val target = BackupFolder.Tree("content://new", "新目录")

        val result = BackupFolderMigrator(store).migrate(BackupFolder.Default, target)

        assertTrue(result is FolderMigrationResult.Failed)
        assertEquals(2, store.listManagedBackups(BackupFolder.Default).size)
        assertTrue(store.listManagedBackups(target).isEmpty())
    }
}

private class FakeBackupDocumentStore : BackupDocumentStore {
    private val files = mutableMapOf<BackupFolder, MutableMap<String, ByteArray>>()
    var corruptNextWrite = false

    fun put(folder: BackupFolder, name: String, value: String) {
        files.getOrPut(folder) { mutableMapOf() }[name] = value.encodeToByteArray()
    }

    override suspend fun listManagedBackups(folder: BackupFolder): List<String> =
        files[folder].orEmpty().keys.filter(::isManagedBackupName).sorted()

    override suspend fun read(folder: BackupFolder, name: String): ByteArray =
        files.getValue(folder).getValue(name)

    override suspend fun write(folder: BackupFolder, name: String, bytes: ByteArray) {
        val written = if (corruptNextWrite) {
            corruptNextWrite = false
            bytes + 0
        } else {
            bytes
        }
        files.getOrPut(folder) { mutableMapOf() }[name] = written
    }

    override suspend fun delete(folder: BackupFolder, name: String) {
        files[folder]?.remove(name)
    }
}
