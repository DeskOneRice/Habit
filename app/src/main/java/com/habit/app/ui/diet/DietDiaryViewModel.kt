package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.stats.summarizeDiet
import com.habit.app.domain.time.DeviceDateProvider
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DietDiaryUiState(
    val selectedDate: LocalDate,
    val records: List<MealRecord> = emptyList(),
    val totalCalories: Int? = null,
    val beverageCups: Int = 0,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DietDiaryViewModel(
    private val repository: DietRepository,
    dateProvider: DeviceDateProvider,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(dateProvider.today())

    val state: StateFlow<DietDiaryUiState> = selectedDate.flatMapLatest { date ->
        repository.observeDay(date.toEpochDay()).map { records ->
            val summary = summarizeDiet(records, date.toEpochDay(), date.toEpochDay())
            DietDiaryUiState(
                selectedDate = date,
                records = records,
                totalCalories = summary.totalCalories,
                beverageCups = summary.beverageCups,
                isLoading = false,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DietDiaryUiState(dateProvider.today()),
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }
}
