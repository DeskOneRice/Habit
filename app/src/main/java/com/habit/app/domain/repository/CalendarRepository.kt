package com.habit.app.domain.repository

import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.HabitHistorySnapshot
import com.habit.app.domain.model.MonthSnapshot
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {
    fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot>

    fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot>

    fun observeHabitHistory(habitId: Long, today: LocalDate): Flow<HabitHistorySnapshot>
}
