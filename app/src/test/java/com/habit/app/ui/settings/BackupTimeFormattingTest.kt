package com.habit.app.ui.settings

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTimeFormattingTest {
    @Test
    fun backupPreviewExplicitlyUsesBeijingTime() {
        val timestamp = Instant.parse("2026-08-03T16:30:45Z").toEpochMilli()

        assertEquals("2026-08-04 00:30（北京时间）", formatBackupTime(timestamp))
    }
}
