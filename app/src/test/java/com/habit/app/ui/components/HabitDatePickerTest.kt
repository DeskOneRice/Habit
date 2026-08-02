package com.habit.app.ui.components

import androidx.compose.ui.Alignment
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitDatePickerTest {
    @Test
    fun weekdayAndDateCellsShareCenteredContentAlignment() {
        assertEquals(Alignment.Center, HABIT_CALENDAR_CELL_ALIGNMENT)
    }

    @Test
    fun august2026StartsWithFiveEmptyMondayFirstCells() {
        val cells = buildMonthCells(YearMonth.of(2026, 8))

        assertEquals(5, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 8, 1), cells[5])
    }

    @Test
    fun leapFebruaryContainsTwentyNineDates() {
        assertEquals(
            29,
            buildMonthCells(YearMonth.of(2028, 2)).filterNotNull().size,
        )
    }

    @Test
    fun weekdayLabelsAreChineseMondayFirst() {
        assertEquals(
            listOf("一", "二", "三", "四", "五", "六", "日"),
            HABIT_WEEKDAY_LABELS,
        )
    }
}
