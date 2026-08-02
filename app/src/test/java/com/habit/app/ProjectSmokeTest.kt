package com.habit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationNamespaceIsStable() {
        assertEquals("com.habit.app", HabitApplication::class.java.packageName)
    }
}
