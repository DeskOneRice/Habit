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
}

data class DrawerDestination(
    val destination: HabitDestination,
    val label: String,
    val symbol: String,
)

val topLevelDestinations = listOf(
    DrawerDestination(HabitDestination.Workbench, "今日工作台", "⌂"),
    DrawerDestination(HabitDestination.Calendar, "习惯日历", "▦"),
    DrawerDestination(HabitDestination.Habits, "我的习惯", "✓"),
    DrawerDestination(HabitDestination.Categories, "分类管理", "◫"),
    DrawerDestination(HabitDestination.Settings, "主题与设置", "⚙"),
)
