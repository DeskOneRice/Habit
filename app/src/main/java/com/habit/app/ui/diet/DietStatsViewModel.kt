package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.DietRangeSummary
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.stats.summarizeDiet
import com.habit.app.domain.time.DeviceDateProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DietStatsUiState(
    val summary: DietRangeSummary,
    val isLoading: Boolean = true,
)

class DietStatsViewModel(
    repository: DietRepository,
    dateProvider: DeviceDateProvider,
) : ViewModel() {
    private val end = dateProvider.today().toEpochDay()
    private val start = end - 6
    val state: StateFlow<DietStatsUiState> = repository.observeRange(start, end).map { records ->
        DietStatsUiState(summarizeDiet(records, start, end), false)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DietStatsUiState(summarizeDiet(emptyList(), start, end)),
    )
}
