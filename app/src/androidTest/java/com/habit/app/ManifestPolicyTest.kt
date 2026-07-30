package com.habit.app

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestPolicyTest {
    @Test
    fun localOnlyAppDisablesAndroidBackup() {
        val applicationInfo = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationInfo

        assertEquals(
            "Local-only habit data must not enter Android backup or restore.",
            0,
            applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }
}
