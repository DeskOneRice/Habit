package com.habit.app.ui.diet

import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.time.DeviceDateProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietDiaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsOnDeviceDateAndAllowsDateSelection() = TestScope(dispatcher).runTest {
        val provider = object : DeviceDateProvider {
            override fun today() = LocalDate.of(2026, 8, 3)
            override val zoneId = ZoneId.of("Asia/Shanghai")
        }
        val vm = DietDiaryViewModel(FakeDietRepository(), provider)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 8, 3), vm.state.value.selectedDate)

        vm.selectDate(LocalDate.of(2026, 8, 2))
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 8, 2), vm.state.value.selectedDate)
    }

    @Test
    fun recentModeGroupsDatesDescendingAndTimesAscending() = TestScope(dispatcher).runTest {
        val repository = FakeDietRepository()
        repository.records.value = listOf(
            record(id = 1, day = 2, time = 20),
            record(id = 2, day = 3, time = 18),
            record(id = 3, day = 3, time = 8),
        )

        val vm = DietDiaryViewModel(repository, FixedDiaryDateProvider)
        advanceUntilIdle()

        assertEquals(DietDiaryMode.RECENT, vm.state.value.mode)
        assertEquals(listOf(3L, 2L), vm.state.value.recentGroups.map { it.epochDay })
        assertEquals(listOf(8L, 18L), vm.state.value.recentGroups.first().records.map { it.occurredAt })
    }

    private fun record(id: Long, day: Long, time: Long) = MealRecord(
        id = id,
        recordType = DietRecordType.MEAL,
        mealType = MealType.LUNCH,
        occurredAt = time,
        recordEpochDay = day,
        description = "记录 $id",
        foodItems = emptyList(),
        calculatedCalories = null,
        finalCalories = null,
        calorieSource = CalorieSource.NONE,
        beverage = null,
        note = "",
        createdAt = time,
        updatedAt = time,
        dietCategoryId = 4,
    )
}

private class FakeDietRepository : DietRepository {
    val records = MutableStateFlow<List<MealRecord>>(emptyList())
    override fun observeAll(): Flow<List<MealRecord>> = records
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = records
    override fun observeRecord(id: Long): Flow<MealRecord?> = MutableStateFlow(null)
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> = records
    override suspend fun save(id: Long?, draft: MealRecordDraft): Long = id ?: 1
    override suspend fun delete(id: Long) = Unit
}

private object FixedDiaryDateProvider : DeviceDateProvider {
    override fun today(): LocalDate = LocalDate.of(2026, 8, 3)
    override val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
}
