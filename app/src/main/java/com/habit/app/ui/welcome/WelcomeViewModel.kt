package com.habit.app.ui.welcome

import androidx.lifecycle.ViewModel
import com.habit.app.domain.model.Habit
import com.habit.app.domain.repository.HabitRepository
import com.habit.app.data.preferences.OnboardingPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class FirstRunDestination {
    Loading,
    Welcome,
    Workbench,
}

internal fun firstRunDestinationFor(
    habits: List<Habit>,
    onboardingCompleted: Boolean,
): FirstRunDestination = if (habits.isEmpty() && !onboardingCompleted) {
    FirstRunDestination.Welcome
} else {
    FirstRunDestination.Workbench
}

class WelcomeViewModel(
    habitRepository: HabitRepository,
    onboardingPreferencesRepository: OnboardingPreferencesRepository,
) : ViewModel() {
    val destination: Flow<FirstRunDestination> = combine(
        habitRepository.observeAll(),
        onboardingPreferencesRepository.completed,
        ::firstRunDestinationFor,
    )
}
