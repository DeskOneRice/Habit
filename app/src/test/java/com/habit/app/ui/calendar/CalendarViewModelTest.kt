package com.habit.app.ui.calendar

import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.HabitHistorySnapshot
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
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
    fun observationsUseInjectedDeviceLocalDateAndFollowSelection() = runTest(dispatcher) {
        val today = LocalDate.of(2031, 2, 3)
        val calendar = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(calendar, ImmediateCheckIns, FixedDateProvider(today))
        advanceUntilIdle()

        assertEquals(YearMonth.of(2031, 2) to today, calendar.monthCalls.last())
        assertEquals(today to today, calendar.dayCalls.last())

        viewModel.nextMonth()
        viewModel.selectDate(LocalDate.of(2031, 3, 4))
        advanceUntilIdle()

        assertEquals(YearMonth.of(2031, 3) to today, calendar.monthCalls.last())
        assertEquals(LocalDate.of(2031, 3, 4) to today, calendar.dayCalls.last())
    }

    @Test
    fun secondToggleForSameHabitIsIgnoredWhileFirstIsInFlight() = runTest(dispatcher) {
        val pending = PendingCheckIns()
        val viewModel = CalendarViewModel(
            RecordingCalendarRepository(),
            pending,
            FixedDateProvider(LocalDate.of(2031, 2, 3)),
        )

        viewModel.toggle(7)
        viewModel.toggle(7)
        advanceUntilIdle()

        assertEquals(1, pending.calls)
        assertTrue(7L in viewModel.state.value.togglingHabitIds)

        pending.result.complete(ToggleResult.Checked)
        advanceUntilIdle()

        assertFalse(7L in viewModel.state.value.togglingHabitIds)
    }

    @Test
    fun toggleUsesDateSelectedWhenButtonWasPressed() = runTest(dispatcher) {
        val today = LocalDate.of(2031, 2, 3)
        val clickedDate = today.minusDays(1)
        val checkIns = RecordingCheckIns()
        val viewModel = CalendarViewModel(
            RecordingCalendarRepository(),
            checkIns,
            FixedDateProvider(today),
        )

        viewModel.selectDate(clickedDate)
        viewModel.toggle(7)
        viewModel.selectDate(today)
        advanceUntilIdle()

        assertEquals(listOf(clickedDate), checkIns.dates)
    }

    @Test
    fun unexpectedToggleFailureShowsRetryMessageAndClearsGuard() = runTest(dispatcher) {
        val viewModel = CalendarViewModel(
            RecordingCalendarRepository(),
            FailingCheckIns,
            FixedDateProvider(LocalDate.of(2031, 2, 3)),
        )

        viewModel.toggle(7)
        advanceUntilIdle()

        assertEquals("打卡失败，请重试", viewModel.state.value.message)
        assertFalse(7L in viewModel.state.value.togglingHabitIds)
    }

    @Test
    fun observationFailuresShowSafeEmptyStateAndSelectionChangesRetry() = runTest(dispatcher) {
        val today = LocalDate.of(2031, 2, 3)
        val calendar = FailingObservationCalendarRepository()
        val viewModel = CalendarViewModel(calendar, ImmediateCheckIns, FixedDateProvider(today))
        advanceUntilIdle()

        assertEquals("日历加载失败，请稍后重试", viewModel.state.value.message)
        assertEquals(YearMonth.from(today), viewModel.state.value.month?.month)
        assertTrue(viewModel.state.value.month?.marksByEpochDay.orEmpty().isEmpty())
        assertEquals(today.toEpochDay(), viewModel.state.value.day?.epochDay)
        assertTrue(viewModel.state.value.day?.habits.orEmpty().isEmpty())
        assertEquals(1, calendar.monthCalls)
        assertEquals(1, calendar.dayCalls)

        viewModel.nextMonth()
        viewModel.selectDate(today.minusDays(1))
        advanceUntilIdle()

        assertEquals(2, calendar.monthCalls)
        assertEquals(2, calendar.dayCalls)
        assertEquals(null, viewModel.state.value.message)
        assertEquals(YearMonth.from(today).plusMonths(1), viewModel.state.value.month?.month)
        assertEquals(today.minusDays(1).toEpochDay(), viewModel.state.value.day?.epochDay)
    }

    @Test
    fun successfulToggleDoesNotHideObservationFailure() = runTest(dispatcher) {
        val viewModel = CalendarViewModel(
            AlwaysFailingObservationCalendarRepository(),
            ImmediateCheckIns,
            FixedDateProvider(LocalDate.of(2031, 2, 3)),
        )
        advanceUntilIdle()
        assertEquals("日历加载失败，请稍后重试", viewModel.state.value.message)

        viewModel.toggle(7)
        advanceUntilIdle()

        assertEquals("日历加载失败，请稍后重试", viewModel.state.value.message)
    }

    @Test
    fun observationCancellationIsNotConvertedToLoadFailure() = runTest(dispatcher) {
        val viewModel = CalendarViewModel(
            CancellationObservationCalendarRepository(),
            ImmediateCheckIns,
            FixedDateProvider(LocalDate.of(2031, 2, 3)),
        )
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.message)
    }

    @Test
    fun changedDeviceDateAndZoneRefreshBeforeSelectionAndToggle() = runTest(dispatcher) {
        val initialToday = LocalDate.of(2031, 2, 3)
        val refreshedToday = initialToday.plusDays(1)
        val provider = MutableDateProvider(initialToday, ZoneId.of("UTC"))
        val calendar = RecordingCalendarRepository()
        val checkIns = RecordingCheckIns()
        val viewModel = CalendarViewModel(calendar, checkIns, provider)
        advanceUntilIdle()

        provider.date = refreshedToday
        provider.zone = ZoneId.of("Asia/Tokyo")
        viewModel.selectDate(refreshedToday)
        advanceUntilIdle()

        assertEquals(refreshedToday, viewModel.today)
        assertEquals(ZoneId.of("Asia/Tokyo"), viewModel.zoneId)
        assertEquals(YearMonth.from(refreshedToday) to refreshedToday, calendar.monthCalls.last())
        assertEquals(refreshedToday to refreshedToday, calendar.dayCalls.last())

        viewModel.toggle(7)
        advanceUntilIdle()

        assertEquals(listOf(refreshedToday), checkIns.dates)
        assertEquals(listOf(refreshedToday), checkIns.todays)
    }

    @Test
    fun changingMonthClearsPreviousSnapshotUntilMatchingMonthArrives() = runTest(dispatcher) {
        val today = LocalDate.of(2031, 2, 3)
        val calendar = ControllableMonthCalendarRepository()
        val viewModel = CalendarViewModel(calendar, ImmediateCheckIns, FixedDateProvider(today))
        runCurrent()
        calendar.emitMonth(YearMonth.of(2031, 2))
        runCurrent()
        assertEquals(YearMonth.of(2031, 2), viewModel.state.value.month?.month)

        viewModel.nextMonth()
        runCurrent()

        assertEquals(YearMonth.of(2031, 3), viewModel.state.value.visibleMonth)
        assertEquals(null, viewModel.state.value.month)

        calendar.emitMonth(YearMonth.of(2031, 3))
        runCurrent()
        assertEquals(YearMonth.of(2031, 3), viewModel.state.value.month?.month)
    }
}

private class FixedDateProvider(private val date: LocalDate) : DeviceDateProvider {
    override fun today(): LocalDate = date
}

private class MutableDateProvider(
    var date: LocalDate,
    var zone: ZoneId,
) : DeviceDateProvider {
    override fun today(): LocalDate = date

    override val zoneId: ZoneId
        get() = zone
}

private class RecordingCalendarRepository : CalendarRepository {
    val monthCalls = mutableListOf<Pair<YearMonth, LocalDate>>()
    val dayCalls = mutableListOf<Pair<LocalDate, LocalDate>>()

    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> {
        monthCalls += month to today
        return flowOf(MonthSnapshot(month, emptyMap(), MonthStats(0, 0, 0, 0f)))
    }

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> {
        dayCalls += date to today
        return flowOf(DaySnapshot(date.toEpochDay(), emptyList()))
    }

    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> = error("Not used")
}

private class ControllableMonthCalendarRepository : CalendarRepository {
    private val monthFlows = mutableMapOf<YearMonth, MutableSharedFlow<MonthSnapshot>>()

    suspend fun emitMonth(month: YearMonth) {
        monthFlows.getOrPut(month) { MutableSharedFlow(replay = 1) }
            .emit(MonthSnapshot(month, emptyMap(), MonthStats(0, 0, 0, 0f)))
    }

    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> =
        monthFlows.getOrPut(month) { MutableSharedFlow(replay = 1) }

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> =
        flowOf(DaySnapshot(date.toEpochDay(), emptyList()))

    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> = error("Not used")
}

private class FailingObservationCalendarRepository : CalendarRepository {
    var monthCalls = 0
    var dayCalls = 0

    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> {
        monthCalls += 1
        return if (monthCalls == 1) {
            flow { throw IllegalStateException("month database unavailable") }
        } else {
            flowOf(MonthSnapshot(month, emptyMap(), MonthStats(0, 0, 0, 0f)))
        }
    }

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> {
        dayCalls += 1
        return if (dayCalls == 1) {
            flow { throw IllegalStateException("day database unavailable") }
        } else {
            flowOf(DaySnapshot(date.toEpochDay(), emptyList()))
        }
    }

    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> = error("Not used")
}

private class AlwaysFailingObservationCalendarRepository : CalendarRepository {
    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> =
        flow { throw IllegalStateException("month database unavailable") }

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> =
        flow { throw IllegalStateException("day database unavailable") }

    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> = error("Not used")
}

private class CancellationObservationCalendarRepository : CalendarRepository {
    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> =
        flow { throw CancellationException("cancel observation") }

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> =
        flow { throw CancellationException("cancel observation") }

    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> = error("Not used")
}

private data object ImmediateCheckIns : CheckInRepository {
    override suspend fun toggle(
        habitId: Long,
        date: LocalDate,
        today: LocalDate,
    ): ToggleResult = ToggleResult.Checked
}

private class RecordingCheckIns : CheckInRepository {
    val dates = mutableListOf<LocalDate>()
    val todays = mutableListOf<LocalDate>()

    override suspend fun toggle(
        habitId: Long,
        date: LocalDate,
        today: LocalDate,
    ): ToggleResult {
        dates += date
        todays += today
        return ToggleResult.Checked
    }
}

private class PendingCheckIns : CheckInRepository {
    val result = CompletableDeferred<ToggleResult>()
    var calls = 0

    override suspend fun toggle(
        habitId: Long,
        date: LocalDate,
        today: LocalDate,
    ): ToggleResult {
        calls += 1
        return result.await()
    }
}

private data object FailingCheckIns : CheckInRepository {
    override suspend fun toggle(
        habitId: Long,
        date: LocalDate,
        today: LocalDate,
    ): ToggleResult = error("database unavailable")
}
