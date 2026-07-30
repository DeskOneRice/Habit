package com.habit.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.Category
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.HabitHistorySnapshot
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.HabitRepository
import com.habit.app.domain.stats.HabitStatistics
import com.habit.app.domain.stats.MonthStats
import com.habit.app.domain.time.DeviceDateProvider
import com.habit.app.domain.time.DeviceDateSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DETAIL_LOAD_FAILURE_MESSAGE = "习惯详情加载失败，请稍后重试"
private const val DETAIL_MISSING_MESSAGE = "习惯不存在或已被删除"
private const val CATEGORY_LOAD_FAILURE_MESSAGE = "分类信息加载失败，请稍后重试"

private sealed interface HistoryResult {
    data object Loading : HistoryResult
    data class Loaded(val snapshot: HabitHistorySnapshot) : HistoryResult
    data class Failed(val message: String) : HistoryResult
}

private sealed interface CategoryResult {
    data class Loaded(val categories: List<Category>) : CategoryResult
    data class Failed(val message: String) : CategoryResult
}

data class HabitDetailUiState(
    val habitId: Long,
    val today: LocalDate,
    val zoneId: ZoneId,
    val visibleMonth: YearMonth,
    val history: HabitHistorySnapshot? = null,
    val categoryName: String = "",
    val monthProgress: MonthStats = MonthStats(0, 0, 0, 0f),
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val message: String? = null,
)

fun habitMonthProgress(
    habit: Habit,
    completedEpochDays: Set<Long>,
    month: YearMonth,
    today: LocalDate,
): MonthStats = HabitStatistics.forMonth(
    month = month,
    habits = listOf(habit),
    completedByHabit = mapOf(habit.id to completedEpochDays),
    today = today,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HabitDetailViewModel(
    private val habitId: Long,
    calendarRepository: CalendarRepository,
    private val habitRepository: HabitRepository,
    categoryRepository: CategoryRepository,
    private val dateProvider: DeviceDateProvider,
) : ViewModel() {
    init {
        require(habitId > 0) { "habitId must be positive" }
    }

    private val deviceDate = MutableStateFlow(dateProvider.snapshot())
    private val visibleMonth = MutableStateFlow(YearMonth.from(deviceDate.value.today))
    private val actionInProgress = MutableStateFlow(false)
    private val actionMessage = MutableStateFlow<String?>(null)

    private val history: Flow<HistoryResult> = deviceDate
        .flatMapLatest { currentDeviceDate ->
            calendarRepository
                .observeHabitHistory(habitId, currentDeviceDate.today)
                .map<HabitHistorySnapshot, HistoryResult>(HistoryResult::Loaded)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(HistoryResult.Failed(detailFailureMessage(failure)))
                }
        }

    private val categories: Flow<CategoryResult> = categoryRepository.observeAll()
        .map<List<Category>, CategoryResult>(CategoryResult::Loaded)
        .catch { failure ->
            if (failure is CancellationException) throw failure
            emit(CategoryResult.Failed(CATEGORY_LOAD_FAILURE_MESSAGE))
        }

    val state: StateFlow<HabitDetailUiState> = combine(
        deviceDate,
        visibleMonth,
        history,
        categories,
    ) { currentDeviceDate, month, historyResult, categoryResult ->
        when (historyResult) {
            HistoryResult.Loading -> HabitDetailUiState(
                habitId = habitId,
                today = currentDeviceDate.today,
                zoneId = currentDeviceDate.zoneId,
                visibleMonth = month,
            )

            is HistoryResult.Failed -> HabitDetailUiState(
                habitId = habitId,
                today = currentDeviceDate.today,
                zoneId = currentDeviceDate.zoneId,
                visibleMonth = month,
                loading = false,
                message = historyResult.message,
            )

            is HistoryResult.Loaded -> {
                val snapshot = historyResult.snapshot
                HabitDetailUiState(
                    habitId = habitId,
                    today = currentDeviceDate.today,
                    zoneId = currentDeviceDate.zoneId,
                    visibleMonth = month,
                    history = snapshot,
                    categoryName = (categoryResult as? CategoryResult.Loaded)
                        ?.categories
                        ?.firstOrNull { it.id == snapshot.habit.categoryId }
                        ?.name
                        .orEmpty(),
                    monthProgress = habitMonthProgress(
                        habit = snapshot.habit,
                        completedEpochDays = snapshot.completedEpochDays,
                        month = month,
                        today = currentDeviceDate.today,
                    ),
                    loading = false,
                    message = (categoryResult as? CategoryResult.Failed)?.message,
                )
            }
        }
    }.combine(actionInProgress) { currentState, inProgress ->
        currentState.copy(actionInProgress = inProgress)
    }.combine(actionMessage) { currentState, currentActionMessage ->
        currentState.copy(message = currentActionMessage ?: currentState.message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HabitDetailUiState(
            habitId = habitId,
            today = deviceDate.value.today,
            zoneId = deviceDate.value.zoneId,
            visibleMonth = visibleMonth.value,
        ),
    )

    fun previousMonth() {
        visibleMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        visibleMonth.update { it.plusMonths(1) }
    }

    fun refreshDeviceDate() {
        val previous = deviceDate.value
        val refreshed = dateProvider.snapshot()
        if (previous == refreshed) return

        val monthFollowedToday = visibleMonth.value == YearMonth.from(previous.today)
        deviceDate.value = refreshed
        if (monthFollowedToday) {
            visibleMonth.value = YearMonth.from(refreshed.today)
        }
    }

    fun archive(onDone: () -> Unit) {
        val currentState = state.value
        if (currentState.actionInProgress || currentState.history?.habit?.archivedEpochDay != null) return
        performDestructiveAction(
            failureMessage = "归档失败，请重试",
            action = {
                refreshDeviceDate()
                habitRepository.archive(habitId, deviceDate.value.today.toEpochDay())
            },
            onDone = onDone,
        )
    }

    fun delete(onDone: () -> Unit) {
        if (state.value.actionInProgress) return
        performDestructiveAction(
            failureMessage = "删除失败，请重试",
            action = { habitRepository.delete(habitId) },
            onDone = onDone,
        )
    }

    private fun performDestructiveAction(
        failureMessage: String,
        action: suspend () -> Unit,
        onDone: () -> Unit,
    ) {
        actionInProgress.value = true
        actionMessage.value = null
        viewModelScope.launch {
            try {
                action()
                onDone()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actionMessage.value = failureMessage
            } finally {
                actionInProgress.value = false
            }
        }
    }
}

private fun detailFailureMessage(failure: Throwable): String =
    if (
        failure is IllegalArgumentException &&
        (
            failure.message?.contains("does not exist", ignoreCase = true) == true ||
                failure.message?.contains("不存在") == true
            )
    ) {
        DETAIL_MISSING_MESSAGE
    } else {
        DETAIL_LOAD_FAILURE_MESSAGE
    }
