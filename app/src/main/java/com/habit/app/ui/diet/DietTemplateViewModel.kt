package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.DietTemplate
import com.habit.app.domain.repository.DietTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DietTemplateUiState(
    val templates: List<DietTemplate> = emptyList(),
    val mealExpanded: Boolean = true,
    val beverageExpanded: Boolean = true,
    val message: String? = null,
) {
    val mealTemplates get() = templates.filter { it.draft.recordType == DietRecordType.MEAL }.sortedBy { it.sortOrder }
    val beverageTemplates get() = templates.filter { it.draft.recordType == DietRecordType.BEVERAGE }.sortedBy { it.sortOrder }
    fun toggleMeal() = copy(mealExpanded = !mealExpanded)
    fun toggleBeverage() = copy(beverageExpanded = !beverageExpanded)
}

class DietTemplateViewModel(private val repository: DietTemplateRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DietTemplateUiState())
    val state: StateFlow<DietTemplateUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { mutableState.value = mutableState.value.copy(templates = it) }
        }
    }

    fun toggleMeal() { mutableState.value = mutableState.value.toggleMeal() }
    fun toggleBeverage() { mutableState.value = mutableState.value.toggleBeverage() }
    fun delete(id: Long) = viewModelScope.launch { runCatching { repository.delete(id) }.onFailure(::showError) }
    fun rename(id: Long, name: String) = viewModelScope.launch { runCatching { repository.rename(id, name) }.onFailure(::showError) }
    fun move(id: Long, offset: Int) = viewModelScope.launch {
        val item = mutableState.value.templates.firstOrNull { it.id == id } ?: return@launch
        val group = if (item.draft.recordType == DietRecordType.MEAL) mutableState.value.mealTemplates else mutableState.value.beverageTemplates
        val from = group.indexOfFirst { it.id == id }
        val to = (from + offset).coerceIn(0, group.lastIndex)
        if (from != to) repository.reorder(group.toMutableList().apply { add(to, removeAt(from)) }.map { it.id })
    }

    private fun showError(error: Throwable) { mutableState.value = mutableState.value.copy(message = error.message) }
}
