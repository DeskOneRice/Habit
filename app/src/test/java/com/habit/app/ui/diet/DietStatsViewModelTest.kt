package com.habit.app.ui.diet

import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.time.DeviceDateProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietStatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultsToSevenDaysAndCanSwitchToThirtyDays() = runTest(dispatcher) {
        val repository = StatsRepository()
        val today = FixedStatsDateProvider.today().toEpochDay()
        repository.records.value = listOf(record(1, today, DietRecordType.MEAL))
        val viewModel = DietStatsViewModel(repository, EmptyStatsCategoryRepository, FixedStatsDateProvider)
        advanceUntilIdle()

        assertEquals(DietStatsRange.SEVEN_DAYS, viewModel.state.value.range)
        assertEquals(today - 6, repository.requestedStart)
        assertEquals(today, repository.requestedEnd)

        viewModel.selectRange(DietStatsRange.THIRTY_DAYS)
        advanceUntilIdle()

        assertEquals(DietStatsRange.THIRTY_DAYS, viewModel.state.value.range)
        assertEquals(today - 29, repository.requestedStart)
        assertEquals(today, repository.requestedEnd)
        assertEquals(1, viewModel.state.value.summary.mealRecordCount)
    }

    private fun record(id: Long, day: Long, type: DietRecordType) = MealRecord(
        id = id,
        recordType = type,
        mealType = MealType.LUNCH,
        occurredAt = 1,
        recordEpochDay = day,
        description = "测试",
        foodItems = emptyList(),
        calculatedCalories = null,
        finalCalories = null,
        calorieSource = CalorieSource.NONE,
        beverage = null,
        note = "",
        createdAt = 1,
        updatedAt = 1,
    )
}

private object EmptyStatsCategoryRepository : DietCategoryRepository {
    override fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>> = flowOf(emptyList())
    override fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>> = flowOf(emptyList())
    override fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>> = flowOf(emptyMap())
    override suspend fun create(scope: DietCategoryScope, name: String): Long = 1
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun setHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit
}

private class StatsRepository : DietRepository {
    val records = MutableStateFlow<List<MealRecord>>(emptyList())
    var requestedStart: Long = Long.MIN_VALUE
    var requestedEnd: Long = Long.MIN_VALUE

    override fun observeAll(): Flow<List<MealRecord>> = records
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = records
    override fun observeRecord(id: Long): Flow<MealRecord?> = MutableStateFlow(null)
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> {
        requestedStart = startEpochDay
        requestedEnd = endEpochDay
        return records
    }
    override suspend fun save(id: Long?, draft: MealRecordDraft): Long = id ?: 1
    override suspend fun delete(id: Long) = Unit
}

private object FixedStatsDateProvider : DeviceDateProvider {
    override fun today(): LocalDate = LocalDate.of(2026, 8, 10)
    override val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
}
