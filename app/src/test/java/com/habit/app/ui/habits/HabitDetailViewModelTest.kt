package com.habit.app.ui.habits

import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.HabitHistorySnapshot
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.HabitRepository
import com.habit.app.domain.stats.HabitStatistics
import com.habit.app.domain.time.DeviceDateProvider
import com.habit.app.testHabit
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshUsesDeviceLocalDateAndRecomputesMonthProgress() = runTest(dispatcher) {
        val initialToday = LocalDate.of(2031, 2, 3)
        val provider = MutableDetailDateProvider(initialToday)
        val calendar = RecordingHistoryRepository()
        val viewModel = HabitDetailViewModel(
            habitId = 7,
            calendarRepository = calendar,
            habitRepository = NoOpHabitRepository,
            categoryRepository = OneCategoryRepository,
            dateProvider = provider,
        )
        advanceUntilIdle()

        assertEquals(listOf(initialToday), calendar.todayCalls)
        assertEquals(2, viewModel.state.value.monthProgress.completedCount)
        assertEquals(3, viewModel.state.value.monthProgress.expectedCount)

        provider.date = initialToday.plusDays(1)
        provider.zone = ZoneId.of("Asia/Tokyo")
        viewModel.refreshDeviceDate()
        advanceUntilIdle()

        assertEquals(initialToday.plusDays(1), calendar.todayCalls.last())
        assertEquals(initialToday.plusDays(1), viewModel.state.value.today)
        assertEquals(ZoneId.of("Asia/Tokyo"), viewModel.state.value.zoneId)
        assertEquals(4, viewModel.state.value.monthProgress.expectedCount)
    }

    @Test
    fun archivedDateBoundsMonthProgressAndFutureMonthHasNoEligibility() {
        val today = LocalDate.of(2031, 2, 10)
        val habit = testHabit(
            id = 7,
            start = LocalDate.of(2031, 2, 2).toEpochDay(),
            archived = LocalDate.of(2031, 2, 5).toEpochDay(),
        )
        val completed = setOf(
            LocalDate.of(2031, 2, 2).toEpochDay(),
            LocalDate.of(2031, 2, 4).toEpochDay(),
            LocalDate.of(2031, 2, 5).toEpochDay(),
        )

        val current = habitMonthProgress(habit, completed, YearMonth.of(2031, 2), today)
        val future = habitMonthProgress(habit, completed, YearMonth.of(2031, 3), today)

        assertEquals(3, current.expectedCount)
        assertEquals(2, current.completedCount)
        assertEquals(0, future.expectedCount)
        assertEquals(0, future.completedCount)
    }

    @Test
    fun repositoryFailureShowsChineseStateButCancellationIsNotConverted() = runTest(dispatcher) {
        val failureViewModel = HabitDetailViewModel(
            habitId = 7,
            calendarRepository = FailingHistoryRepository(IllegalStateException("database unavailable")),
            habitRepository = NoOpHabitRepository,
            categoryRepository = OneCategoryRepository,
            dateProvider = MutableDetailDateProvider(LocalDate.of(2031, 2, 3)),
        )
        advanceUntilIdle()
        assertEquals("习惯详情加载失败，请稍后重试", failureViewModel.state.value.message)

        val cancelledViewModel = HabitDetailViewModel(
            habitId = 7,
            calendarRepository = FailingHistoryRepository(CancellationException("cancel")),
            habitRepository = NoOpHabitRepository,
            categoryRepository = OneCategoryRepository,
            dateProvider = MutableDetailDateProvider(LocalDate.of(2031, 2, 3)),
        )
        advanceUntilIdle()
        assertNull(cancelledViewModel.state.value.message)
    }
}

private class MutableDetailDateProvider(
    var date: LocalDate,
    var zone: ZoneId = ZoneId.of("UTC"),
) : DeviceDateProvider {
    override fun today(): LocalDate = date
    override val zoneId: ZoneId get() = zone
}

private class RecordingHistoryRepository : CalendarRepository {
    val todayCalls = mutableListOf<LocalDate>()

    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> {
        todayCalls += today
        val habit = testHabit(
            id = habitId,
            start = LocalDate.of(2031, 2, 1).toEpochDay(),
        )
        val completed = setOf(
            LocalDate.of(2031, 2, 1).toEpochDay(),
            LocalDate.of(2031, 2, 2).toEpochDay(),
        )
        return flowOf(
            HabitHistorySnapshot(
                habit = habit,
                completedEpochDays = completed,
                stats = HabitStatistics.forHabit(completed, today),
            ),
        )
    }

    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> =
        error("Not used")

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> =
        error("Not used")
}

private class FailingHistoryRepository(private val failure: Throwable) : CalendarRepository {
    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> = flow { throw failure }

    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> =
        error("Not used")

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> =
        error("Not used")
}

private data object NoOpHabitRepository : HabitRepository {
    override fun observeAll() = flowOf(emptyList<com.habit.app.domain.model.Habit>())
    override fun observeById(id: Long) = flowOf(null)
    override suspend fun create(draft: com.habit.app.domain.model.HabitDraft): Long = error("Not used")
    override suspend fun update(id: Long, draft: com.habit.app.domain.model.HabitDraft) = Unit
    override suspend fun archive(id: Long, archivedEpochDay: Long) = Unit
    override suspend fun delete(id: Long) = Unit
}

private data object OneCategoryRepository : CategoryRepository {
    private val category = com.habit.app.domain.model.Category(
        id = 1,
        name = "学习",
        isPreset = true,
        isHidden = false,
        sortOrder = 0,
        createdAt = 1,
        updatedAt = 1,
    )

    override fun observeVisible() = flowOf(listOf(category))
    override fun observeAll() = flowOf(listOf(category))
    override suspend fun create(name: String): Long = error("Not used")
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun setPresetHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit
}
