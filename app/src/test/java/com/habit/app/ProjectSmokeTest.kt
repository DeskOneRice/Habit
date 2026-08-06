package com.habit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationNamespaceIsStable() {
        assertEquals("com.habit.app", HabitApplication::class.java.packageName)
    }

    @Test
    fun releaseMetadataIsHabitZeroPointFivePointZero() {
        assertEquals("0.5.0", BuildConfig.VERSION_NAME)
        assertEquals(10, BuildConfig.VERSION_CODE)
    }
}
