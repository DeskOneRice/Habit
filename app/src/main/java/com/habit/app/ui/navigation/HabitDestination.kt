package com.habit.app.ui.navigation

sealed interface HabitDestination {
    val route: String

    data object Welcome : HabitDestination { override val route = "welcome" }
    data object Workbench : HabitDestination { override val route = "workbench" }
    data object Calendar : HabitDestination { override val route = "calendar" }
    data object Habits : HabitDestination { override val route = "habits" }
    data object HabitEditor : HabitDestination { override val route = "habit_editor?habitId={habitId}"; fun route(id: Long? = null) = if (id == null) "habit_editor" else "habit_editor?habitId=$id" }
    data object HabitDetail : HabitDestination {
        override val route = "habit_detail/{habitId}"
        fun route(id: Long) = "habit_detail/$id"
    }
    data object Settings : HabitDestination { override val route = "settings" }
    data object Categories : HabitDestination { override val route = "categories" }
    data object DietDiary : HabitDestination { override val route = "diet" }
    data object DietEditor : HabitDestination {
        override val route = "diet_editor?recordId={recordId}"
        fun route(id: Long? = null) = if (id == null) "diet_editor" else "diet_editor?recordId=$id"
    }
    data object DietStats : HabitDestination { override val route = "diet_stats" }
    data object DietSettings : HabitDestination { override val route = "diet_settings" }
}

data class DrawerDestination(
    val destination: HabitDestination,
    val label: String,
    val symbol: String,
)

val habitDestinations = listOf(
    DrawerDestination(HabitDestination.Calendar, "习惯日历", "▦"),
    DrawerDestination(HabitDestination.Habits, "我的习惯", "✓"),
    DrawerDestination(HabitDestination.Categories, "分类管理", "◫"),
)

val dietDestinations = listOf(
    DrawerDestination(HabitDestination.DietDiary, "饮食日记", "☕"),
    DrawerDestination(HabitDestination.DietStats, "饮食统计", "⌁"),
    DrawerDestination(HabitDestination.DietSettings, "饮食设置", "⚙"),
)

val drawerTopLevelRoutes = buildSet {
    add(HabitDestination.Workbench.route)
    add(HabitDestination.Settings.route)
    addAll(habitDestinations.map { it.destination.route })
    addAll(dietDestinations.map { it.destination.route })
}
