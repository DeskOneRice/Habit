package com.habit.app.ui.navigation

import com.habit.app.testHabit
import com.habit.app.ui.welcome.FirstRunDestination
import com.habit.app.ui.welcome.firstRunDestinationFor
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun existingUsersStartAtWorkbench() {
        assertEquals(
            FirstRunDestination.Workbench,
            firstRunDestinationFor(listOf(testHabit())),
        )
    }

    @Test
    fun newUsersStillSeeWelcome() {
        assertEquals(FirstRunDestination.Welcome, firstRunDestinationFor(emptyList()))
    }

    @Test
    fun drawerContainsExactlyFiveTopLevelRoutes() {
        assertEquals(
            listOf("workbench", "calendar", "habits", "categories", "settings"),
            topLevelDestinations.map { it.destination.route },
        )
    }
}
