package com.habit.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CheckInRepository
import com.habit.app.domain.repository.ToggleResult
import com.habit.app.domain.stats.MonthStats
import com.habit.app.domain.time.DeviceDateSnapshot
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CALENDAR_LOAD_FAILURE_MESSAGE = "日历加载失败，请稍后重试"

private data class MonthRequest(
    val month: YearMonth,
    val deviceDate: DeviceDateSnapshot,
)

private data class DayRequest(
    val date: LocalDate,
    val deviceDate: DeviceDateSnapshot,
)

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val zoneId: ZoneId,
    val month: MonthSnapshot? = null,
    val day: DaySnapshot? = null,
    val togglingHabitIds: Set<Long> = emptySet(),
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val calendarRepository: CalendarRepository,
    private val checkInRepository: CheckInRepository,
    private val dateProvider: DeviceDateProvider,
) : ViewModel() {
    private val deviceDate = MutableStateFlow(dateProvider.snapshot())

    val today: LocalDate
        get() = deviceDate.value.today
    val zoneId: ZoneId
        get() = deviceDate.value.zoneId

    private val visibleMonth = MutableStateFlow(YearMonth.from(deviceDate.value.today))
    private val selectedDate = MutableStateFlow(deviceDate.value.today)
    private val togglingHabitIds = MutableStateFlow<Set<Long>>(emptySet())
    private val monthError = MutableStateFlow<String?>(null)
    private val dayError = MutableStateFlow<String?>(null)
    private val actionMessage = MutableStateFlow<String?>(null)
    private val toggleGuard = Any()

    private val month = combine(visibleMonth, deviceDate) { observedMonth, currentDeviceDate ->
        MonthRequest(observedMonth, currentDeviceDate)
    }.distinctUntilChanged().flatMapLatest { request ->
        calendarRepository.observeMonth(request.month, request.deviceDate.today)
            .map<MonthSnapshot, MonthSnapshot?> { snapshot ->
                snapshot.takeIf { it.month == request.month }
            }
            .onEach { snapshot ->
                if (snapshot != null) monthError.value = null
            }
            .onStart { emit(null) }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                monthError.value = CALENDAR_LOAD_FAILURE_MESSAGE
                emit(
                    MonthSnapshot(
                        month = request.month,
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
    private val day = combine(selectedDate, deviceDate) { observedDate, currentDeviceDate ->
        DayRequest(observedDate, currentDeviceDate)
    }.distinctUntilChanged().flatMapLatest { request ->
        calendarRepository.observeDay(request.date, request.deviceDate.today)
            .map<DaySnapshot, DaySnapshot?> { snapshot ->
                snapshot.takeIf { it.epochDay == request.date.toEpochDay() }
            }
            .onEach { snapshot ->
                if (snapshot != null) dayError.value = null
            }
            .onStart { emit(null) }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                dayError.value = CALENDAR_LOAD_FAILURE_MESSAGE
                emit(DaySnapshot(epochDay = request.date.toEpochDay(), habits = emptyList()))
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
        deviceDate,
        month,
        day,
    ) { visibleMonth, selectedDate, deviceDate, month, day ->
        CalendarUiState(
            visibleMonth = visibleMonth,
            selectedDate = selectedDate,
            today = deviceDate.today,
            zoneId = deviceDate.zoneId,
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
        initialValue = CalendarUiState(
            visibleMonth = YearMonth.from(deviceDate.value.today),
            selectedDate = deviceDate.value.today,
            today = deviceDate.value.today,
            zoneId = deviceDate.value.zoneId,
        ),
    )

    fun previousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        refreshDeviceDate()
        selectedDate.value = date
    }

    fun toggle(habitId: Long) {
        refreshDeviceDate()
        val date = selectedDate.value
        val currentToday = deviceDate.value.today
        synchronized(toggleGuard) {
            if (habitId in togglingHabitIds.value) return
            togglingHabitIds.value = togglingHabitIds.value + habitId
        }
        viewModelScope.launch {
            try {
                when (val result = checkInRepository.toggle(habitId, date, currentToday)) {
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

    fun refreshDeviceDate() {
        val previous = deviceDate.value
        val refreshed = dateProvider.snapshot()
        if (refreshed == previous) return

        val selectedDateFollowedToday = selectedDate.value == previous.today
        val visibleMonthFollowedToday = visibleMonth.value == YearMonth.from(previous.today)
        deviceDate.value = refreshed
        if (selectedDateFollowedToday) {
            selectedDate.value = refreshed.today
        }
        if (visibleMonthFollowedToday) {
            visibleMonth.value = YearMonth.from(refreshed.today)
        }
    }
}
