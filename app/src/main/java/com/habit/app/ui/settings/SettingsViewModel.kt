package com.habit.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.ui.theme.HabitThemeId
import com.habit.app.ui.theme.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedTheme: HabitThemeId = HabitThemeId.SKY_BLUE,
    val message: String? = null,
)

class SettingsViewModel(
    private val themeRepository: ThemeRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        themeRepository.theme,
        message,
    ) { theme, currentMessage ->
        SettingsUiState(
            selectedTheme = theme,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(),
    )

    fun selectTheme(theme: HabitThemeId) {
        viewModelScope.launch {
            try {
                themeRepository.setTheme(theme)
                message.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "主题已应用，但可能无法在重启后保留"
            }
        }
    }
}
