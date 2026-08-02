package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.data.preferences.DietPreferences
import com.habit.app.data.preferences.DietPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DietSettingsUiState(
    val preferences: DietPreferences = DietPreferences(),
    val goalText: String = "",
    val message: String? = null,
)

class DietSettingsViewModel(private val repository: DietPreferencesRepository) : ViewModel() {
    private val goalText = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    val state: StateFlow<DietSettingsUiState> = combine(repository.preferences, goalText, message) { preferences, text, currentMessage ->
        DietSettingsUiState(preferences, text.ifBlank { preferences.dailyGoalKcal?.toString().orEmpty() }, currentMessage)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DietSettingsUiState())

    fun updateGoalText(value: String) { goalText.value = value.filter(Char::isDigit); message.value = null }
    fun saveGoal(enabled: Boolean) = viewModelScope.launch {
        try {
            repository.setGoal(enabled, goalText.value.toIntOrNull())
            message.value = if (enabled) "每日目标已保存" else "每日目标已关闭"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { message.value = error.message }
    }
}
