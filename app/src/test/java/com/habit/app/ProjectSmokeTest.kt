package com.habit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationNamespaceIsStable() {
        assertEquals("com.habit.app", HabitApplication::class.java.packageName)
    }

    @Test
    fun releaseMetadataIsHabitZeroPointFour() {
        assertEquals("0.4.0", BuildConfig.VERSION_NAME)
        assertEquals(8, BuildConfig.VERSION_CODE)
    }
}
