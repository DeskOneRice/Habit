package com.habit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationNamespaceIsStable() {
        assertEquals("com.habit.app", HabitApplication::class.java.packageName)
    }

    @Test
    fun releaseMetadataIsHabitZeroPointFivePointOne() {
        assertEquals("0.5.1", BuildConfig.VERSION_NAME)
        assertEquals(11, BuildConfig.VERSION_CODE)
    }
}
