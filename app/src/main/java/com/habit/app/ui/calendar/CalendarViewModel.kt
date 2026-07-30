package com.habit.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CheckInRepository
import com.habit.app.domain.repository.ToggleResult
import com.habit.app.domain.stats.MonthStats
import com.habit.app.domain.time.DeviceDateProvider
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CALENDAR_LOAD_FAILURE_MESSAGE = "日历加载失败，请稍后重试"

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val month: MonthSnapshot? = null,
    val day: DaySnapshot? = null,
    val togglingHabitIds: Set<Long> = emptySet(),
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val calendarRepository: CalendarRepository,
    private val checkInRepository: CheckInRepository,
    dateProvider: DeviceDateProvider,
) : ViewModel() {
    val today: LocalDate = dateProvider.today()
    val zoneId: ZoneId = dateProvider.zoneId

    private val visibleMonth = MutableStateFlow(YearMonth.from(today))
    private val selectedDate = MutableStateFlow(today)
    private val togglingHabitIds = MutableStateFlow<Set<Long>>(emptySet())
    private val monthError = MutableStateFlow<String?>(null)
    private val dayError = MutableStateFlow<String?>(null)
    private val actionMessage = MutableStateFlow<String?>(null)
    private val toggleGuard = Any()

    private val month = visibleMonth.flatMapLatest { observedMonth ->
        calendarRepository.observeMonth(observedMonth, today)
            .onEach { monthError.value = null }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                monthError.value = CALENDAR_LOAD_FAILURE_MESSAGE
                emit(
                    MonthSnapshot(
                        month = observedMonth,
                        marksByEpochDay = emptyMap(),
                        stats = MonthStats(
                            activeDays = 0,
                            completedCount = 0,
                            expectedCount = 0,
                            completionRate = 0f,
                        ),
                    ),
                )
            }
    }
    private val day = selectedDate.flatMapLatest { date ->
        calendarRepository.observeDay(date, today)
            .onEach { dayError.value = null }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                dayError.value = CALENDAR_LOAD_FAILURE_MESSAGE
                emit(DaySnapshot(epochDay = date.toEpochDay(), habits = emptyList()))
            }
    }
    private val userMessage = combine(monthError, dayError, actionMessage) {
            currentMonthError,
            currentDayError,
            currentActionMessage,
        ->
        currentMonthError ?: currentDayError ?: currentActionMessage
    }

    val state: StateFlow<CalendarUiState> = combine(
        visibleMonth,
        selectedDate,
        month,
        day,
    ) { visibleMonth, selectedDate, month, day ->
        CalendarUiState(
            visibleMonth = visibleMonth,
            selectedDate = selectedDate,
            month = month,
            day = day,
        )
    }.combine(togglingHabitIds) { state, toggling ->
        state.copy(togglingHabitIds = toggling)
    }.combine(userMessage) { state, currentMessage ->
        state.copy(message = currentMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CalendarUiState(YearMonth.from(today), today),
    )

    fun previousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun toggle(habitId: Long) {
        val date = selectedDate.value
        synchronized(toggleGuard) {
            if (habitId in togglingHabitIds.value) return
            togglingHabitIds.value = togglingHabitIds.value + habitId
        }
        viewModelScope.launch {
            try {
                when (val result = checkInRepository.toggle(habitId, date, today)) {
                    ToggleResult.Checked,
                    ToggleResult.Unchecked,
                    -> actionMessage.value = null

                    is ToggleResult.Rejected -> actionMessage.value = result.reason
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actionMessage.value = "打卡失败，请重试"
            } finally {
                synchronized(toggleGuard) {
                    togglingHabitIds.value = togglingHabitIds.value - habitId
                }
            }
        }
    }
}
