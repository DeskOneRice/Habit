package com.habit.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HabitBackupCodecTest {
    @Test
    fun roundTripPreservesDatabaseTimesAndEpochDays() {
        val source = sampleBackup()

        val decoded = HabitBackupCodec.decode(HabitBackupCodec.encode(source))

        assertEquals(source, decoded)
    }

    @Test
    fun newerSchemaIsRejected() {
        val json = """{"format":"habit-backup","schemaVersion":99}"""

        assertThrows(UnsupportedBackupVersionException::class.java) {
            HabitBackupCodec.decode(json)
        }
    }

    @Test
    fun danglingHabitCategoryIsRejected() {
        val invalid = sampleBackup().copy(
            habits = listOf(sampleBackup().habits.single().copy(categoryId = 999)),
        )

        assertThrows(InvalidBackupException::class.java) {
            HabitBackupCodec.validate(invalid)
        }
    }

    private fun sampleBackup() = HabitBackup(
        appVersion = "0.2.1",
        exportedAt = 1_785_686_400_000,
        preferencesUpdatedAt = 1_785_686_300_000,
        categories = listOf(
            BackupCategory(1, "学习", true, false, 0, 100, 101),
        ),
        habits = listOf(
            BackupHabit(4, "背单词", "emoji:📚", 0xFF8DB9CC, 1, 20_300, null, 0, 110, 120),
        ),
        checkIns = listOf(
            BackupCheckIn(9, 4, 20_301, 130, 140),
        ),
        preferences = BackupPreferences("sky_blue", listOf("emoji:📚")),
    )
}
