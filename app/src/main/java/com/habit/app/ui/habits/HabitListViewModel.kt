package com.habit.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.Category
import com.habit.app.domain.model.Habit
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitListUiState(val categories: List<Category> = emptyList(), val active: List<Habit> = emptyList(), val archived: List<Habit> = emptyList())

class HabitListViewModel(habits: HabitRepository, categories: CategoryRepository) : ViewModel() {
    val state: StateFlow<HabitListUiState> = combine(habits.observeAll(), categories.observeAll()) { all, groups ->
        HabitListUiState(groups, all.filter { it.archivedEpochDay == null }, all.filter { it.archivedEpochDay != null })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitListUiState())
    private val repository = habits
    fun archive(id: Long, onDone: () -> Unit) = viewModelScope.launch { repository.archive(id, LocalDate.now().toEpochDay()); onDone() }
    fun delete(id: Long, onDone: () -> Unit) = viewModelScope.launch { repository.delete(id); onDone() }
}
