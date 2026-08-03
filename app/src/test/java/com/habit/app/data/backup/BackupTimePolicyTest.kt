package com.habit.app.data.backup

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTimePolicyTest {
    @Test
    fun backupFileNameUsesBeijingTime() {
        val timestamp = Instant.parse("2026-08-03T16:30:45Z").toEpochMilli()

        assertEquals(
            "Habit-Backup-20260804-003045.habitbackup.zip",
            backupFileName(timestamp),
        )
    }
}
