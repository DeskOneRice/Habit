package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.CheckInEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.HabitEntity
import com.habit.app.domain.stats.HabitStats
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCalendarRepositoryTest {
    private lateinit var db: HabitDatabase
    private lateinit var repository: RoomCalendarRepository
    private var notStartedHabitId = 0L
    private var archivedHabitId = 0L
    private var emptyHistoryHabitId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomCalendarRepository(db.habitDao(), db.checkInDao())
        val categoryId = db.categoryDao().insert(
            CategoryEntity(
                name = "Study",
                isPreset = false,
                isHidden = false,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        repeat(5) { index ->
            val habitId = db.habitDao().insert(
                HabitEntity(
                    name = "Habit $index",
                    iconKey = "icon_$index",
                    themeColor = 0xFF8DB9CC,
                    categoryId = categoryId,
                    startEpochDay = LocalDate.of(2026, 7, 1).toEpochDay(),
                    archivedEpochDay = null,
                    sortOrder = index + 1,
                    createdAt = 100,
                    updatedAt = 100,
                ),
            )
            db.checkInDao().insert(
                CheckInEntity(0, habitId, LocalDate.of(2026, 7, 29).toEpochDay(), 100, 100),
            )
        }
        notStartedHabitId = db.habitDao().insert(
            HabitEntity(
                name = "Starts later",
                iconKey = "later",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = LocalDate.of(2026, 7, 21).toEpochDay(),
                archivedEpochDay = null,
                sortOrder = 6,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        archivedHabitId = db.habitDao().insert(
            HabitEntity(
                name = "Archived",
                iconKey = "archive",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = LocalDate.of(2026, 7, 1).toEpochDay(),
                archivedEpochDay = LocalDate.of(2026, 7, 20).toEpochDay(),
                sortOrder = 7,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        emptyHistoryHabitId = db.habitDao().insert(
            HabitEntity(
                name = "Empty history",
                iconKey = "empty",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = LocalDate.of(2026, 7, 1).toEpochDay(),
                archivedEpochDay = null,
                sortOrder = 8,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun marksAreSortedAndOverflowCountIsDerivable() = runTest {
        val snapshot = repository.observeMonth(
            YearMonth.of(2026, 7),
            LocalDate.of(2026, 7, 30),
        ).first()
        val marks = snapshot.marksByEpochDay[LocalDate.of(2026, 7, 29).toEpochDay()].orEmpty()

        assertEquals(listOf(1, 2, 3, 4, 5), marks.map { it.sortOrder })
        assertEquals(4, marks.take(4).size)
        assertEquals(1, (marks.size - 4).coerceAtLeast(0))
    }

    @Test
    fun daySnapshotExcludesNotStartedAndArchivedHabits() = runTest {
        val snapshot = repository.observeDay(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 30),
        ).first()

        assertEquals(
            setOf(notStartedHabitId, archivedHabitId),
            (1L..emptyHistoryHabitId).toSet() - snapshot.habits.map { it.habit.id }.toSet(),
        )
    }

    @Test
    fun futureDaySnapshotKeepsEligibleHabitRowsVisible() = runTest {
        val snapshot = repository.observeDay(
            LocalDate.of(2026, 7, 31),
            LocalDate.of(2026, 7, 30),
        ).first()

        assertEquals(
            listOf(1L, 2L, 3L, 4L, 5L, notStartedHabitId, emptyHistoryHabitId),
            snapshot.habits.map { it.habit.id },
        )
        assertTrue(snapshot.habits.none { it.checked })
    }

    @Test
    fun daySnapshotExposesCheckInCreatedAt() = runTest {
        val snapshot = repository.observeDay(
            LocalDate.of(2026, 7, 29),
            LocalDate.of(2026, 7, 30),
        ).first()

        val checkedHabit = snapshot.habits.first { it.habit.id == 1L }
        assertTrue(checkedHabit.checked)
        assertEquals(100L, checkedHabit.checkedAt)
    }

    @Test
    fun observeForHabitReturnsOnlyTargetHabitCheckIns() = runTest {
        db.checkInDao().insert(
            CheckInEntity(
                habitId = 2L,
                checkInEpochDay = LocalDate.of(2026, 7, 28).toEpochDay(),
                createdAt = 200,
                updatedAt = 200,
            ),
        )

        val checkIns = db.checkInDao().observeForHabit(1L).first()

        assertEquals(listOf(1L), checkIns.map { it.habitId })
        assertEquals(listOf(LocalDate.of(2026, 7, 29).toEpochDay()), checkIns.map { it.checkInEpochDay })
    }

    @Test
    fun habitHistoryDoesNotIncludeOtherHabitCheckIns() = runTest {
        val before = repository.observeHabitHistory(1L, LocalDate.of(2026, 7, 30)).first()
        db.checkInDao().insert(
            CheckInEntity(
                habitId = 2L,
                checkInEpochDay = LocalDate.of(2026, 7, 28).toEpochDay(),
                createdAt = 200,
                updatedAt = 200,
            ),
        )

        val after = repository.observeHabitHistory(1L, LocalDate.of(2026, 7, 30)).first()

        assertEquals(before, after)
    }

    @Test
    fun emptyHabitHistoryHasNoCompletionsOrStreaks() = runTest {
        val snapshot = repository.observeHabitHistory(
            emptyHistoryHabitId,
            LocalDate.of(2026, 7, 30),
        ).first()

        assertEquals(emptySet<Long>(), snapshot.completedEpochDays)
        assertEquals(HabitStats(0, 0, 0), snapshot.stats)
    }

    @Test
    fun missingHabitHistoryThrowsAnIllegalArgumentException() = runTest {
        try {
            repository.observeHabitHistory(999L, LocalDate.of(2026, 7, 30)).first()
            fail("Expected a missing habit to fail.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Habit 999 does not exist.", expected.message)
        }
    }
}
