package com.habit.app.ui.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.Category
import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.HabitHistorySnapshot
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.ai.previousCompleteWeek
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.CheckInRepository
import com.habit.app.domain.repository.ToggleResult
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.repository.DietTemplateRepository
import com.habit.app.domain.model.DietTemplate
import com.habit.app.domain.stats.summarizeDiet
import com.habit.app.domain.stats.MonthStats
import com.habit.app.domain.time.DeviceDateProvider
import com.habit.app.domain.time.DeviceDateSnapshot
import com.habit.app.domain.time.HabitTimePolicy
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkbenchHabitItem(
    val habit: Habit,
    val categoryName: String,
    val checked: Boolean,
    val currentStreak: Int,
)

data class RecentDay(
    val date: LocalDate,
    val iconKeys: List<String>,
)

enum class WeeklyInsightStatus { NEEDS_MODEL, READY_TO_GENERATE, SAVED }

data class WeeklyInsightSummary(
    val status: WeeklyInsightStatus,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val savedReport: AiWeeklyReport? = null,
)

internal fun buildWeeklyInsightSummary(
    now: Instant,
    hasUsableWeeklyModel: Boolean,
    reports: List<AiWeeklyReport>,
): WeeklyInsightSummary {
    val today = now.atZone(HabitTimePolicy.zoneId).toLocalDate()
    val week = previousCompleteWeek(today)
    val saved = reports.firstOrNull {
        it.startEpochDay == week.start.toEpochDay() && it.endEpochDay == week.endInclusive.toEpochDay()
    }
    return WeeklyInsightSummary(
        status = when {
            saved != null -> WeeklyInsightStatus.SAVED
            hasUsableWeeklyModel -> WeeklyInsightStatus.READY_TO_GENERATE
            else -> WeeklyInsightStatus.NEEDS_MODEL
        },
        startDate = week.start,
        endDate = week.endInclusive,
        savedReport = saved,
    )
}

data class WorkbenchUiState(
    val today: LocalDate,
    val habits: List<WorkbenchHabitItem> = emptyList(),
    val recentDays: List<RecentDay> = emptyList(),
    val monthStats: MonthStats = MonthStats(0, 0, 0, 0f),
    val togglingHabitIds: Set<Long> = emptySet(),
    val message: String? = null,
    val isLoading: Boolean = true,
    val dietRecordCount: Int = 0,
    val dietCalories: Int? = null,
    val beverageCups: Int = 0,
    val quickDietTemplates: List<DietTemplate> = emptyList(),
    val weeklyInsight: WeeklyInsightSummary? = null,
) {
    val completedCount: Int get() = habits.count(WorkbenchHabitItem::checked)
    val totalCount: Int get() = habits.size
    val progress: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    val longestCurrentStreak: Int get() = habits.maxOfOrNull(WorkbenchHabitItem::currentStreak) ?: 0
}

internal fun buildWorkbenchState(
    today: LocalDate,
    day: DaySnapshot,
    recentDays: List<RecentDay>,
    month: MonthSnapshot,
    histories: Map<Long, HabitHistorySnapshot>,
    categories: List<Category>,
): WorkbenchUiState {
    val categoryNames = categories.associate { it.id to it.name }
    return WorkbenchUiState(
        today = today,
        habits = day.habits.map { dayHabit ->
            WorkbenchHabitItem(
                habit = dayHabit.habit,
                categoryName = categoryNames[dayHabit.habit.categoryId].orEmpty(),
                checked = dayHabit.checked,
                currentStreak = histories[dayHabit.habit.id]?.stats?.currentStreak ?: 0,
            )
        },
        recentDays = recentDays,
        monthStats = month.stats,
        isLoading = false,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkbenchViewModel(
    private val calendarRepository: CalendarRepository,
    private val categoryRepository: CategoryRepository,
    private val checkInRepository: CheckInRepository,
    private val dateProvider: DeviceDateProvider,
    private val dietRepository: DietRepository,
    private val dietTemplateRepository: DietTemplateRepository? = null,
    private val weeklyInsightSummary: Flow<WeeklyInsightSummary>,
) : ViewModel() {
    private val deviceDate = MutableStateFlow(dateProvider.snapshot())
    private val togglingHabitIds = MutableStateFlow<Set<Long>>(emptySet())
    private val actionMessage = MutableStateFlow<String?>(null)
    private val toggleGuard = Any()

    private val content: Flow<WorkbenchUiState> = deviceDate.flatMapLatest { snapshot ->
        contentFor(snapshot)
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(
            WorkbenchUiState(
                today = deviceDate.value.today,
                message = "工作台加载失败，请重试",
                isLoading = false,
            ),
        )
    }

    val state: StateFlow<WorkbenchUiState> = combine(
        content,
        togglingHabitIds,
        actionMessage,
    ) { current, toggling, action ->
        current.copy(
            togglingHabitIds = toggling,
            message = action ?: current.message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        WorkbenchUiState(today = deviceDate.value.today),
    )

    private fun contentFor(snapshot: DeviceDateSnapshot): Flow<WorkbenchUiState> {
        val today = snapshot.today
        val habitContent = calendarRepository.observeDay(today, today).flatMapLatest { day ->
            val historyFlows = day.habits.map { dayHabit ->
                calendarRepository.observeHabitHistory(dayHabit.habit.id, today)
            }
            val histories: Flow<Map<Long, HabitHistorySnapshot>> = if (historyFlows.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(historyFlows) { values -> values.associateBy { it.habit.id } }
            }
            val recentDays = combine(
                (6 downTo 0).map { offset ->
                    val date = today.minusDays(offset.toLong())
                    calendarRepository.observeDay(date, today)
                },
            ) { days ->
                days.map { snapshotForDay ->
                    RecentDay(
                        date = LocalDate.ofEpochDay(snapshotForDay.epochDay),
                        iconKeys = snapshotForDay.habits
                            .filter { it.checked }
                            .map { it.habit.iconKey },
                    )
                }
            }
            combine(
                calendarRepository.observeMonth(YearMonth.from(today), today),
                categoryRepository.observeAll(),
                histories,
                recentDays,
            ) { month, categories, historyById, week ->
                buildWorkbenchState(today, day, week, month, historyById, categories)
            }
        }
        val dietContent = combine(habitContent, dietRepository.observeDay(today.toEpochDay())) { current, records ->
            val summary = summarizeDiet(records, today.toEpochDay(), today.toEpochDay())
            current.copy(
                dietRecordCount = records.size,
                dietCalories = summary.totalCalories,
                beverageCups = summary.beverageCups,
            )
        }
        val templates = dietTemplateRepository?.observeAll() ?: flowOf(emptyList())
        return combine(dietContent, templates, weeklyInsightSummary) { current, available, insight ->
            current.copy(
                quickDietTemplates = available.sortedBy { it.sortOrder }.take(4),
                weeklyInsight = insight,
            )
        }
    }

    fun toggle(habitId: Long) {
        refreshDeviceDate()
        val today = deviceDate.value.today
        synchronized(toggleGuard) {
            if (habitId in togglingHabitIds.value) return
            togglingHabitIds.value += habitId
        }
        viewModelScope.launch {
            try {
                when (val result = checkInRepository.toggle(habitId, today, today)) {
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
                    togglingHabitIds.value -= habitId
                }
            }
        }
    }

    fun refreshDeviceDate() {
        val refreshed = dateProvider.snapshot()
        if (refreshed != deviceDate.value) deviceDate.value = refreshed
    }
}
