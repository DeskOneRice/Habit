package com.habit.app.domain.stats

import com.habit.app.domain.model.Habit
import java.time.LocalDate

object DatePolicy {
    fun isEligible(habit: Habit, date: LocalDate, today: LocalDate): Boolean {
        val epochDay = date.toEpochDay()
        return epochDay >= habit.startEpochDay &&
            epochDay < (habit.archivedEpochDay ?: Long.MAX_VALUE) &&
            !date.isAfter(today)
    }
}
