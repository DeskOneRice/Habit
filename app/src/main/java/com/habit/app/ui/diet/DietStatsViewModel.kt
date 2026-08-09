package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.DietRangeSummary
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.stats.summarizeDiet
import com.habit.app.domain.time.DeviceDateProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class DietStatsRange(val days: Long, val title: String) {
    SEVEN_DAYS(7, "最近 7 天"),
    THIRTY_DAYS(30, "最近 30 天"),
}

data class DietStatsUiState(
    val summary: DietRangeSummary,
    val range: DietStatsRange = DietStatsRange.SEVEN_DAYS,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DietStatsViewModel(
    repository: DietRepository,
    dateProvider: DeviceDateProvider,
) : ViewModel() {
    private val end = dateProvider.today().toEpochDay()
    private val selectedRange = MutableStateFlow(DietStatsRange.SEVEN_DAYS)
    private fun start(range: DietStatsRange) = end - range.days + 1

    val state: StateFlow<DietStatsUiState> = selectedRange.flatMapLatest { range ->
        val start = start(range)
        repository.observeRange(start, end).map { records ->
            DietStatsUiState(
                summary = summarizeDiet(records, start, end),
                range = range,
                isLoading = false,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DietStatsUiState(summarizeDiet(emptyList(), start(DietStatsRange.SEVEN_DAYS), end)),
    )

    fun selectRange(range: DietStatsRange) {
        selectedRange.value = range
    }
}
