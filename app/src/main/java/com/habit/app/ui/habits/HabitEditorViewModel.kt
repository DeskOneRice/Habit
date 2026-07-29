package com.habit.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.HabitDraft
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitEditorUiState(
    val name: String = "",
    val iconKey: String = "book",
    val themeColor: Long = 0xFF8DB9CC,
    val categoryId: Long? = null,
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    val nameError: String? = null,
    val saving: Boolean = false,
    val habitId: Long? = null,
)

fun validateHabitName(raw: String): String? =
    if (raw.trim().length in 1..30) null else "请输入 1～30 个字符的习惯名称"

class HabitEditorViewModel(
    private val habitRepository: HabitRepository,
    private val categoryRepository: CategoryRepository,
    private val habitId: Long? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(HabitEditorUiState(habitId = habitId))
    val state: StateFlow<HabitEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = categoryRepository.observeVisible().first()
            _state.update { it.copy(categoryId = it.categoryId ?: categories.firstOrNull()?.id) }
            habitId?.let { id -> habitRepository.observeById(id).first()?.let { habit ->
                _state.value = HabitEditorUiState(habit.name, habit.iconKey, habit.themeColor, habit.categoryId, habit.startEpochDay, habitId = id)
            } }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun onEmojiChange(value: String) = _state.update { it.copy(iconKey = value) }
    fun onColorChange(value: Long) = _state.update { it.copy(themeColor = value) }
    fun onCategoryChange(value: Long) = _state.update { it.copy(categoryId = value) }
    fun onStartDateChange(value: Long) = _state.update { it.copy(startEpochDay = value) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.saving) return
        val error = validateHabitName(current.name)
        if (error != null || current.categoryId == null) {
            _state.update { it.copy(nameError = error ?: "请选择分类") }; return
        }
        _state.update { it.copy(saving = true, nameError = null) }
        viewModelScope.launch {
            val draft = HabitDraft(current.name.trim(), current.iconKey, current.themeColor, current.categoryId, current.startEpochDay)
            if (current.habitId == null) habitRepository.create(draft) else habitRepository.update(current.habitId, draft)
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }
    fun archive(id: Long, onDone: () -> Unit) = viewModelScope.launch { habitRepository.archive(id, LocalDate.now().toEpochDay()); onDone() }
    fun delete(id: Long, onDone: () -> Unit) = viewModelScope.launch { habitRepository.delete(id); onDone() }
}
