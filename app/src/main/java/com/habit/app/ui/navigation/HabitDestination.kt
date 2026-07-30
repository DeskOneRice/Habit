package com.habit.app.ui.navigation

sealed interface HabitDestination {
    val route: String

    data object Welcome : HabitDestination { override val route = "welcome" }
    data object Calendar : HabitDestination { override val route = "calendar" }
    data object Habits : HabitDestination { override val route = "habits" }
    data object HabitEditor : HabitDestination { override val route = "habit_editor?habitId={habitId}"; fun route(id: Long? = null) = if (id == null) "habit_editor" else "habit_editor?habitId=$id" }
    data object HabitDetail : HabitDestination {
        override val route = "habit_detail/{habitId}"
        fun route(id: Long) = "habit_detail/$id"
    }
    data object Settings : HabitDestination { override val route = "settings" }
    data object Categories : HabitDestination { override val route = "categories" }
}
