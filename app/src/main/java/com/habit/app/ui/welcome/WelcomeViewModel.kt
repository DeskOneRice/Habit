package com.habit.app.ui.welcome

import androidx.lifecycle.ViewModel
import com.habit.app.domain.model.Habit
import com.habit.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class FirstRunDestination {
    Loading,
    Welcome,
    Workbench,
}

internal fun firstRunDestinationFor(habits: List<Habit>): FirstRunDestination =
    if (habits.isEmpty()) FirstRunDestination.Welcome else FirstRunDestination.Workbench

class WelcomeViewModel(habitRepository: HabitRepository) : ViewModel() {
    val destination: Flow<FirstRunDestination> = habitRepository.observeAll()
        .map(::firstRunDestinationFor)
}
