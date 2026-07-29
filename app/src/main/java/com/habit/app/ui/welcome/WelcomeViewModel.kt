package com.habit.app.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class FirstRunDestination {
    Loading,
    Welcome,
    Calendar,
}

class WelcomeViewModel(habitRepository: HabitRepository) : ViewModel() {
    val destination: StateFlow<FirstRunDestination> = habitRepository.observeAll()
        .map { habits ->
            if (habits.isEmpty()) FirstRunDestination.Welcome else FirstRunDestination.Calendar
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FirstRunDestination.Loading,
        )
}
