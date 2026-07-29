package com.habit.app.domain.stats

import com.habit.app.domain.model.Habit
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatePolicyTest {
    private val habit = Habit(
        id = 1,
        name = "背单词",
        iconKey = "book",
        themeColor = 0xFF8DB9CC,
        categoryId = 1,
        startEpochDay = LocalDate.of(2026, 7, 10).toEpochDay(),
        archivedEpochDay = LocalDate.of(2026, 7, 20).toEpochDay(),
        sortOrder = 0,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun rejectsBeforeStart() {
        assertFalse(DatePolicy.isEligible(habit, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 30)))
    }

    @Test
    fun acceptsBeforeArchive() {
        assertTrue(DatePolicy.isEligible(habit, LocalDate.of(2026, 7, 19), LocalDate.of(2026, 7, 30)))
    }

    @Test
    fun acceptsStartDate() {
        assertTrue(DatePolicy.isEligible(habit, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 30)))
    }

    @Test
    fun rejectsArchiveDateAndFuture() {
        assertFalse(DatePolicy.isEligible(habit, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 30)))
        assertFalse(DatePolicy.isEligible(habit.copy(archivedEpochDay = null), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 30)))
    }
}
