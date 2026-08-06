package com.habit.app.ui.navigation

import com.habit.app.testHabit
import com.habit.app.ui.welcome.FirstRunDestination
import com.habit.app.ui.welcome.firstRunDestinationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun existingUsersStartAtWorkbench() {
        assertEquals(
            FirstRunDestination.Workbench,
            firstRunDestinationFor(listOf(testHabit()), onboardingCompleted = false),
        )
    }

    @Test
    fun newUsersStillSeeWelcome() {
        assertEquals(
            FirstRunDestination.Welcome,
            firstRunDestinationFor(emptyList(), onboardingCompleted = false),
        )
    }

    @Test
    fun skippedUsersStartAtWorkbenchWithoutHabits() {
        assertEquals(
            FirstRunDestination.Workbench,
            firstRunDestinationFor(emptyList(), onboardingCompleted = true),
        )
    }

    @Test
    fun drawerContainsWorkbenchAndModuleRoutes() {
        assertEquals(
            listOf("workbench", "calendar", "habits", "categories_drawer", "diet", "diet_templates", "diet_stats", "diet_settings", "settings"),
            topLevelDestinations.map { it.destination.route },
        )
    }

    @Test
    fun drawerNavigationAlwaysReturnsToModuleRoot() {
        assertEquals(false, drawerNavigationPolicy.saveState)
        assertEquals(false, drawerNavigationPolicy.restoreState)
    }

    @Test
    fun dietDetailRouteIsDistinctFromEditorRoute() {
        assertEquals("diet/42", HabitDestination.DietDetail.route(42))
        assertNotEquals(HabitDestination.DietEditor.route(42), HabitDestination.DietDetail.route(42))
    }
}
