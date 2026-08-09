package com.habit.app.ui.diet

import java.time.LocalDate
import java.time.LocalTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.mealTypeDisplayOrder
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.time.DeviceDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun lateNightIsBetweenDinnerAndSnack() {
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.LATE_NIGHT, MealType.SNACK),
            mealTypeDisplayOrder,
        )
    }

    @Test
    fun savingMealPreservesSelectedDietCategory() = runTest(dispatcher) {
        val repository = RecordingDietRepository()
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = repository,
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
        )
        advanceUntilIdle()

        viewModel.selectDietCategory(3L)
        viewModel.save {}
        advanceUntilIdle()

        assertEquals(3L, repository.lastSaved?.dietCategoryId)
    }

    @Test
    fun savingBeverageUsesOneTemperatureFieldAndClearsLegacyIceLevel() = runTest(dispatcher) {
        val repository = RecordingDietRepository()
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = repository,
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
        )
        advanceUntilIdle()

        viewModel.update {
            copy(
                recordType = DietRecordType.BEVERAGE,
                beverageName = "奶茶",
                temperature = "正常冰",
            )
        }
        advanceUntilIdle()
        viewModel.save {}
        advanceUntilIdle()

        assertEquals("正常冰", repository.lastSaved?.beverage?.temperature)
        assertEquals("", repository.lastSaved?.beverage?.iceLevel)
    }

    @Test
    fun selectingTimeKeepsDateAndNormalizesSeconds() {
        val initial = state(
            date = LocalDate.of(2026, 8, 3),
            time = LocalTime.of(8, 0),
        )

        val changed = initial.withTime(LocalTime.of(21, 45, 36))

        assertEquals(LocalDate.of(2026, 8, 3), changed.date)
        assertEquals(LocalTime.of(21, 45), changed.time)
    }

    @Test
    fun selectingDateKeepsTime() {
        val initial = state(
            date = LocalDate.of(2026, 8, 3),
            time = LocalTime.of(8, 20),
        )

        val changed = initial.withDate(LocalDate.of(2027, 1, 15))

        assertEquals(LocalDate.of(2027, 1, 15), changed.date)
        assertEquals(LocalTime.of(8, 20), changed.time)
    }

    @Test
    fun fourthPhotoIsRejectedWithoutChangingDraft() {
        val initial = state(LocalDate.of(2026, 8, 3), LocalTime.NOON).copy(
            photos = (1..3).map { DietPhoto(relativePath = "staging/$it.jpg", sortOrder = it - 1) },
        )

        val changed = initial.withAddedPhotos(
            listOf(DietPhoto(relativePath = "staging/4.jpg", sortOrder = 0)),
        )

        assertEquals(initial.photos, changed.photos)
        assertEquals("每条记录最多添加 3 张照片", changed.message)
        assertFalse(changed.canAddPhoto)
    }

    private fun state(date: LocalDate, time: LocalTime) = DietEditorUiState(
        date = date,
        time = time,
    )
}

private object FixedDateProvider : DeviceDateProvider {
    override fun today(): LocalDate = LocalDate.of(2026, 8, 3)
    override val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
}

private class RecordingDietRepository : DietRepository {
    var lastSaved: MealRecordDraft? = null

    override fun observeAll(): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override fun observeRecord(id: Long): Flow<MealRecord?> = MutableStateFlow(null)
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override suspend fun save(id: Long?, draft: MealRecordDraft): Long {
        lastSaved = draft
        return id ?: 1L
    }
    override suspend fun delete(id: Long) = Unit
}

private class FakeDietCategoryRepository : DietCategoryRepository {
    private val categories = listOf(
        category(1, DietCategoryScope.MEAL, "家常菜"),
        category(3, DietCategoryScope.MEAL, "零食"),
        category(4, DietCategoryScope.MEAL, "其他餐食"),
        category(5, DietCategoryScope.BEVERAGE, "咖啡"),
    )

    override fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>> =
        MutableStateFlow(categories.filter { it.scope == scope })
    override fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>> = observeAll(scope)
    override fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())
    override suspend fun create(scope: DietCategoryScope, name: String): Long = 99L
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun setHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit

    private companion object {
        fun category(id: Long, scope: DietCategoryScope, name: String) = DietCategory(
            id = id,
            scope = scope,
            name = name,
            isPreset = true,
            isHidden = false,
            sortOrder = id.toInt(),
            createdAt = 1,
            updatedAt = 1,
        )
    }
}
