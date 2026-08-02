package com.habit.app.ui.workbench

import com.habit.app.domain.model.CalendarMark
import com.habit.app.domain.model.Category
import com.habit.app.domain.model.DayHabit
import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.HabitHistorySnapshot
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.stats.HabitStats
import com.habit.app.domain.stats.MonthStats
import com.habit.app.testHabit
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkbenchViewModelTest {
    @Test
    fun buildStateCombinesProgressCategoryStreakWeekAndMonth() {
        val today = LocalDate.of(2031, 2, 3)
        val first = testHabit(id = 1, start = today.minusDays(10).toEpochDay())
        val second = testHabit(id = 2, start = today.minusDays(10).toEpochDay(), sortOrder = 1)
        val day = DaySnapshot(
            epochDay = today.toEpochDay(),
            habits = listOf(DayHabit(first, true, 100), DayHabit(second, false, null)),
        )
        val week = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            RecentDay(
                date = date,
                iconKeys = if (date == today) listOf("book", "emoji:🧪") else emptyList(),
            )
        }
        val month = MonthSnapshot(
            month = YearMonth.from(today),
            marksByEpochDay = mapOf(
                today.toEpochDay() to listOf(CalendarMark("book", 1, 0)),
            ),
            stats = MonthStats(5, 10, 4, 0.5f),
        )
        val histories = mapOf(
            1L to HabitHistorySnapshot(first, setOf(today.toEpochDay()), HabitStats(8, 6, 7)),
            2L to HabitHistorySnapshot(second, emptySet(), HabitStats(0, 0, 0)),
        )
        val categories = listOf(Category(1, "学习", true, false, 0, 1, 1))

        val state = buildWorkbenchState(today, day, week, month, histories, categories)

        assertEquals(1, state.completedCount)
        assertEquals(2, state.totalCount)
        assertEquals(0.5f, state.progress)
        assertEquals(6, state.longestCurrentStreak)
        assertEquals("学习", state.habits.first().categoryName)
        assertEquals(7, state.recentDays.size)
        assertEquals(listOf("book", "emoji:🧪"), state.recentDays.last().iconKeys)
        assertEquals(4, state.monthStats.activeDays)
    }

    @Test
    fun emptyStateHasZeroProgress() {
        val today = LocalDate.of(2031, 2, 3)
        val state = buildWorkbenchState(
            today = today,
            day = DaySnapshot(today.toEpochDay(), emptyList()),
            recentDays = emptyList(),
            month = MonthSnapshot(YearMonth.from(today), emptyMap(), MonthStats(0, 0, 0, 0f)),
            histories = emptyMap(),
            categories = emptyList(),
        )

        assertEquals(0f, state.progress)
        assertEquals(0, state.longestCurrentStreak)
    }
}
