package com.habit.app.ui.theme

import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val theme: Flow<HabitThemeId>

    suspend fun setTheme(theme: HabitThemeId)
}
