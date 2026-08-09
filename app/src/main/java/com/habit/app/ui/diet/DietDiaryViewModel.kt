package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.stats.summarizeDiet
import com.habit.app.domain.time.DeviceDateProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf

enum class DietDiaryMode { RECENT, DAY }

data class DietDayGroup(
    val epochDay: Long,
    val records: List<MealRecord>,
)

data class DietDiaryUiState(
    val selectedDate: LocalDate,
    val records: List<MealRecord> = emptyList(),
    val totalCalories: Int? = null,
    val beverageCups: Int = 0,
    val isLoading: Boolean = true,
    val mode: DietDiaryMode = DietDiaryMode.RECENT,
    val recentGroups: List<DietDayGroup> = emptyList(),
    val categoryNames: Map<Long, String> = emptyMap(),
)

class DietDiaryViewModel(
    private val repository: DietRepository,
    dateProvider: DeviceDateProvider,
    categoryRepository: DietCategoryRepository? = null,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(dateProvider.today())
    private val mode = MutableStateFlow(DietDiaryMode.RECENT)

    private val mealCategories = categoryRepository?.observeAll(DietCategoryScope.MEAL) ?: flowOf(emptyList())
    private val beverageCategories = categoryRepository?.observeAll(DietCategoryScope.BEVERAGE) ?: flowOf(emptyList())

    val state: StateFlow<DietDiaryUiState> = combine(
        selectedDate,
        mode,
        repository.observeAll(),
        mealCategories,
        beverageCategories,
    ) { date, selectedMode, allRecords, meals, beverages ->
        val dayRecords = allRecords
            .filter { it.recordEpochDay == date.toEpochDay() }
            .sortedWith(compareByDescending(MealRecord::occurredAt).thenByDescending(MealRecord::id))
        val summary = summarizeDiet(dayRecords, date.toEpochDay(), date.toEpochDay())
        val groups = allRecords
            .groupBy(MealRecord::recordEpochDay)
            .toSortedMap(compareByDescending { it })
            .map { (epochDay, records) ->
                DietDayGroup(
                    epochDay,
                    records.sortedWith(compareByDescending(MealRecord::occurredAt).thenByDescending(MealRecord::id)),
                )
            }
        DietDiaryUiState(
            selectedDate = date,
            records = dayRecords,
            totalCalories = summary.totalCalories,
            beverageCups = summary.beverageCups,
            isLoading = false,
            mode = selectedMode,
            recentGroups = groups,
            categoryNames = (meals + beverages).associate { it.id to it.name },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DietDiaryUiState(dateProvider.today()),
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun selectMode(value: DietDiaryMode) {
        mode.value = value
    }
}
