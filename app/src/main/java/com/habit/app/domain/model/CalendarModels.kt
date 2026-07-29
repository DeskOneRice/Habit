package com.habit.app.domain.model

import com.habit.app.domain.stats.HabitStats
import com.habit.app.domain.stats.MonthStats
import java.time.YearMonth

data class DayHabit(
    val habit: Habit,
    val checked: Boolean,
    val checkedAt: Long?,
)

data class DaySnapshot(
    val epochDay: Long,
    val habits: List<DayHabit>,
)

data class CalendarMark(
    val iconKey: String,
    val habitId: Long,
    val sortOrder: Int,
)

data class MonthSnapshot(
    val month: YearMonth,
    val marksByEpochDay: Map<Long, List<CalendarMark>>,
    val stats: MonthStats,
)

data class HabitHistorySnapshot(
    val habit: Habit,
    val completedEpochDays: Set<Long>,
    val stats: HabitStats,
)
