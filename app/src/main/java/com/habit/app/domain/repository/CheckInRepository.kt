package com.habit.app.domain.repository

import java.time.LocalDate

interface CheckInRepository {
    suspend fun toggle(habitId: Long, date: LocalDate, today: LocalDate): ToggleResult
}

sealed interface ToggleResult {
    data object Checked : ToggleResult
    data object Unchecked : ToggleResult
    data class Rejected(val reason: String) : ToggleResult
}
