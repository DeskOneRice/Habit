package com.habit.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HabitBackupArchiveTest {
    @Test
    fun v3ArchiveRoundTripsMetadataAndPhotoBytes() {
        val photo = byteArrayOf(1, 2, 3, 4)
        val backup = emptyBackup().copy(
            mealRecords = listOf(
                BackupMealRecord(5, "MEAL", "LUNCH", 1, 1, "午餐", null, null, "NONE", "", 1, 1),
            ),
            dietPhotos = listOf(BackupDietPhoto(1, 5, null, "library/a.jpg", 0, 10)),
        )
        val output = ByteArrayOutputStream()

        HabitBackupArchive.write(output, backup) { if (it == "library/a.jpg") photo.inputStream() else null }
        val target = kotlin.io.path.createTempDirectory("habit-archive-").toFile()
        val decoded = HabitBackupArchive.read(ByteArrayInputStream(output.toByteArray()), target)

        assertEquals(backup, decoded.backup)
        assertArrayEquals(photo, decoded.photos.getValue("library/a.jpg").readBytes())
        target.deleteRecursively()
    }

    @Test
    fun archiveRejectsTraversalEntry() {
        val output = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("photos/../../escape.jpg"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(InvalidBackupException::class.java) {
            HabitBackupArchive.read(ByteArrayInputStream(output.toByteArray()), File(System.getProperty("java.io.tmpdir"), "habit-traversal"))
        }
    }

    private fun emptyBackup() = HabitBackup(
        appVersion = "0.4.0",
        exportedAt = 1,
        preferencesUpdatedAt = 1,
        categories = emptyList(),
        habits = emptyList(),
        checkIns = emptyList(),
        preferences = BackupPreferences("sky_blue", emptyList()),
    )
}
