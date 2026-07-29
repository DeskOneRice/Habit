package com.habit.app.domain.stats

import com.habit.app.testHabit
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitStatisticsTest {
    @Test
    fun emptyHistoryHasNoStreaks() {
        assertEquals(
            HabitStats(total = 0, currentStreak = 0, longestStreak = 0),
            HabitStatistics.forHabit(emptySet(), LocalDate.of(2026, 7, 30)),
        )
    }

    @Test
    fun currentStreakFallsBackToYesterdayWhenTodayIsMissing() {
        val today = LocalDate.of(2026, 7, 30)
        val dates = setOf(
            LocalDate.of(2026, 7, 27).toEpochDay(),
            LocalDate.of(2026, 7, 28).toEpochDay(),
            LocalDate.of(2026, 7, 29).toEpochDay(),
        )

        assertEquals(3, HabitStatistics.forHabit(dates, today).currentStreak)
    }

    @Test
    fun longestStreakUsesTheLargestConsecutiveRun() {
        val dates = setOf(
            LocalDate.of(2026, 7, 20).toEpochDay(),
            LocalDate.of(2026, 7, 21).toEpochDay(),
            LocalDate.of(2026, 7, 23).toEpochDay(),
            LocalDate.of(2026, 7, 24).toEpochDay(),
            LocalDate.of(2026, 7, 25).toEpochDay(),
        )

        assertEquals(3, HabitStatistics.forHabit(dates, LocalDate.of(2026, 7, 25)).longestStreak)
    }

    @Test
    fun monthRateUsesDailyEligibility() {
        val month = YearMonth.of(2026, 7)
        val habits = listOf(testHabit(start = 1, archived = null))
        val completed = mapOf(habits.single().id to setOf(LocalDate.of(2026, 7, 1).toEpochDay()))

        val result = HabitStatistics.forMonth(month, habits, completed, LocalDate.of(2026, 7, 2))

        assertEquals(2, result.expectedCount)
        assertEquals(1, result.completedCount)
        assertEquals(0.5f, result.completionRate)
    }

    @Test
    fun futureMonthHasNoExpectedOrCompletedCheckIns() {
        val result = HabitStatistics.forMonth(
            month = YearMonth.of(2026, 8),
            habits = listOf(testHabit()),
            completedByHabit = emptyMap(),
            today = LocalDate.of(2026, 7, 30),
        )

        assertEquals(MonthStats(0, 0, 0, 0f), result)
    }

    @Test
    fun archivedDateIsExcludedFromTheMonthDenominator() {
        val habit = testHabit(
            start = LocalDate.of(2026, 7, 1).toEpochDay(),
            archived = LocalDate.of(2026, 7, 2).toEpochDay(),
        )
        val result = HabitStatistics.forMonth(
            month = YearMonth.of(2026, 7),
            habits = listOf(habit),
            completedByHabit = mapOf(habit.id to setOf(LocalDate.of(2026, 7, 2).toEpochDay())),
            today = LocalDate.of(2026, 7, 3),
        )

        assertEquals(MonthStats(0, 1, 0, 0f), result)
    }

    @Test
    fun activeDaysCountsSharedCompletionDateOnce() {
        val habits = listOf(testHabit(id = 1), testHabit(id = 2))
        val date = LocalDate.of(2026, 7, 1).toEpochDay()
        val result = HabitStatistics.forMonth(
            month = YearMonth.of(2026, 7),
            habits = habits,
            completedByHabit = mapOf(1L to setOf(date), 2L to setOf(date)),
            today = LocalDate.of(2026, 7, 1),
        )

        assertEquals(MonthStats(2, 2, 1, 1f), result)
    }
}
