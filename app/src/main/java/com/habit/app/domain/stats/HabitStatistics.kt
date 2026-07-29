package com.habit.app.domain.stats

import com.habit.app.domain.model.Habit
import java.time.LocalDate
import java.time.YearMonth

data class HabitStats(
    val total: Int,
    val currentStreak: Int,
    val longestStreak: Int,
)

data class MonthStats(
    val completedCount: Int,
    val expectedCount: Int,
    val activeDays: Int,
    val completionRate: Float,
)

object HabitStatistics {
    fun forHabit(completedEpochDays: Set<Long>, today: LocalDate): HabitStats {
        val sorted = completedEpochDays.sorted()
        var longest = 0
        var run = 0
        var previous: Long? = null
        for (day in sorted) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }

        var cursor = if (today.toEpochDay() in completedEpochDays) today else today.minusDays(1)
        var current = 0
        while (cursor.toEpochDay() in completedEpochDays) {
            current += 1
            cursor = cursor.minusDays(1)
        }
        return HabitStats(sorted.size, current, longest)
    }

    fun forMonth(
        month: YearMonth,
        habits: List<Habit>,
        completedByHabit: Map<Long, Set<Long>>,
        today: LocalDate,
    ): MonthStats {
        val last = minOf(month.atEndOfMonth(), today)
        if (last.isBefore(month.atDay(1))) return MonthStats(0, 0, 0, 0f)

        var expected = 0
        val completedDates = mutableSetOf<Long>()
        var completed = 0
        for (dayNumber in 1..last.dayOfMonth) {
            val date = month.atDay(dayNumber)
            for (habit in habits) {
                if (DatePolicy.isEligible(habit, date, today)) {
                    expected += 1
                    if (date.toEpochDay() in completedByHabit[habit.id].orEmpty()) {
                        completed += 1
                        completedDates += date.toEpochDay()
                    }
                }
            }
        }
        return MonthStats(
            completed,
            expected,
            completedDates.size,
            if (expected == 0) 0f else completed.toFloat() / expected,
        )
    }
}
