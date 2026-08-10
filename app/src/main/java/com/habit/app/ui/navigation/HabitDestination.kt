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
    data object Categories : HabitDestination {
        override val route = "categories?section={section}"
        fun route(section: String) = "categories?section=$section"
    }
    data object DrawerCategories : HabitDestination { override val route = "categories_drawer" }
    data object DietDiary : HabitDestination { override val route = "diet" }
    data object DietDetail : HabitDestination {
        override val route = "diet/{recordId}"
        fun route(id: Long) = "diet/$id"
    }
    data object DietEditor : HabitDestination {
        override val route = "diet_editor?recordId={recordId}&repeatId={repeatId}&templateId={templateId}"
        fun route(id: Long? = null) = if (id == null) "diet_editor" else "diet_editor?recordId=$id"
        fun repeatRoute(id: Long) = "diet_editor?repeatId=$id"
        fun templateRoute(id: Long) = "diet_editor?templateId=$id"
    }
    data object DietTemplates : HabitDestination { override val route = "diet_templates" }
    data object DietStats : HabitDestination { override val route = "diet_stats" }
    data object DietSettings : HabitDestination { override val route = "diet_settings" }
    data object AiReports : HabitDestination { override val route = "ai_reports" }
    data object AiReportHistory : HabitDestination { override val route = "ai_reports/history" }
    data object AiReportDetail : HabitDestination {
        override val route = "ai_reports/detail/{startEpochDay}"
        fun route(startEpochDay: Long) = "ai_reports/detail/$startEpochDay"
    }
    data object AiSettings : HabitDestination { override val route = "ai_settings" }
    data object AiModelEditor : HabitDestination {
        override val route = "ai_model_editor?modelId={modelId}"
        fun route(id: Long? = null) = if (id == null) "ai_model_editor" else "ai_model_editor?modelId=$id"
    }
}

data class DrawerDestination(
    val destination: HabitDestination,
    val label: String,
    val symbol: String,
)

val habitDestinations = listOf(
    DrawerDestination(HabitDestination.Calendar, "习惯日历", "📅"),
    DrawerDestination(HabitDestination.Habits, "我的习惯", "✅"),
    DrawerDestination(HabitDestination.DrawerCategories, "分类管理", "🗂️"),
)

val dietDestinations = listOf(
    DrawerDestination(HabitDestination.DietDiary, "饮食日记", "🍽️"),
    DrawerDestination(HabitDestination.DietTemplates, "饮食模板", "📋"),
    DrawerDestination(HabitDestination.DietStats, "饮食统计", "📊"),
    DrawerDestination(HabitDestination.DietSettings, "饮食设置", "⚙️"),
)

val aiDestinations = listOf(
    DrawerDestination(HabitDestination.AiReports, "综合周报", "📝"),
    DrawerDestination(HabitDestination.AiSettings, "模型配置", "🤖"),
)

val topLevelDestinations = listOf(
    DrawerDestination(HabitDestination.Workbench, "今日工作台", "🏠"),
) + habitDestinations + dietDestinations + aiDestinations + DrawerDestination(HabitDestination.Settings, "主题与设置", "🎨")

val drawerTopLevelRoutes = topLevelDestinations.map { it.destination.route }.toSet()

internal data class DrawerNavigationPolicy(
    val saveState: Boolean,
    val restoreState: Boolean,
)

internal val drawerNavigationPolicy = DrawerNavigationPolicy(
    saveState = false,
    restoreState = false,
)
