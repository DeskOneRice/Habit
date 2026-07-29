package com.habit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationIdIsStable() {
        assertEquals("com.habit.app", BuildConfig.APPLICATION_ID)
    }
}
