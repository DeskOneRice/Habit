package com.habit.app.data.preferences

import com.habit.app.data.backup.BackupPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupPreferencePolicyTest {
    @Test
    fun importedPreferencesWinOnlyWhenTheirTimestampIsNewer() {
        val current = TimestampedBackupPreferences(
            BackupPreferences("sky_blue", listOf("emoji:📚")),
            updatedAt = 20,
        )
        val newer = TimestampedBackupPreferences(
            BackupPreferences("soft_pink", listOf("emoji:🎨")),
            updatedAt = 30,
        )
        val older = newer.copy(updatedAt = 10)

        assertEquals(newer, choosePreferences(current, newer))
        assertEquals(current, choosePreferences(current, older))
    }
}
