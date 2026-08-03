package com.habit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationNamespaceIsStable() {
        assertEquals("com.habit.app", HabitApplication::class.java.packageName)
    }

    @Test
    fun releaseMetadataIsHabitZeroPointFourPointOne() {
        assertEquals("0.4.1", BuildConfig.VERSION_NAME)
        assertEquals(9, BuildConfig.VERSION_CODE)
    }
}
