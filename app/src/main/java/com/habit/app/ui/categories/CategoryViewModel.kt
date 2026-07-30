package com.habit.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.Category
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryUiState(
    val categories: List<Category> = emptyList(),
    val habitCounts: Map<Long, Int> = emptyMap(),
    val saving: Boolean = false,
)

class CategoryViewModel(private val categories: CategoryRepository, habits: HabitRepository) : ViewModel() {
    private val saving = MutableStateFlow(false)

    val state: StateFlow<CategoryUiState> = combine(
        categories.observeAll(),
        habits.observeAll(),
        saving,
    ) { groups, all, isSaving ->
        CategoryUiState(
            categories = groups,
            habitCounts = all.groupingBy { it.categoryId }.eachCount(),
            saving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState())

    fun create(name: String, onDone: () -> Unit) = viewModelScope.launch {
        if (name.trim().isNotEmpty() && !saving.value) {
            saving.value = true
            try {
                categories.create(name)
                onDone()
            } finally {
                saving.value = false
            }
        }
    }

    fun rename(id: Long, name: String, onDone: () -> Unit) = viewModelScope.launch {
        if (name.trim().isNotEmpty() && !saving.value) {
            saving.value = true
            try {
                categories.rename(id, name)
                onDone()
            } finally {
                saving.value = false
            }
        }
    }
    fun setPresetHidden(id: Long, hidden: Boolean) = viewModelScope.launch { categories.setPresetHidden(id, hidden) }
    fun migrateAndDelete(source: Long, target: Long, onDone: () -> Unit) = viewModelScope.launch { if (source != target) { categories.migrateAndDelete(source, target); onDone() } }
}
