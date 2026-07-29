package com.habit.app.ui.theme

enum class HabitThemeId {
    SKY_BLUE,
    SOFT_PINK,
    SAGE_GREEN,
    MIST_PURPLE,
    NEUTRAL_GRAY,
    ;

    companion object {
        fun fromStored(value: String?): HabitThemeId =
            entries.firstOrNull { it.name == value } ?: SKY_BLUE
    }
}
