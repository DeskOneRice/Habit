package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.HabitEntity
import com.habit.app.domain.repository.ToggleResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCheckInRepositoryTest {
    private var database: HabitDatabase? = null

    @After
    fun close() {
        database?.close()
    }

    private suspend fun repositoryWithHabit(
        start: LocalDate,
        archivedEpochDay: Long? = null,
    ): RoomCheckInRepository {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        database = db
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
        db.habitDao().insert(
            HabitEntity(
                name = "Vocabulary",
                iconKey = "book",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = start.toEpochDay(),
                archivedEpochDay = archivedEpochDay,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        return RoomCheckInRepository(
            db,
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
        )
    }

    @Test
    fun rejectsFutureAndBeforeStartDates() = runTest {
        val repository = repositoryWithHabit(start = LocalDate.of(2026, 7, 10))

        assertEquals(
            ToggleResult.Rejected("该日期不在习惯有效范围内"),
            repository.toggle(1, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 30)),
        )
        assertEquals(
            ToggleResult.Rejected("不能打卡未来日期"),
            repository.toggle(1, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 30)),
        )
    }

    @Test
    fun secondToggleRemovesExistingCheckIn() = runTest {
        val repository = repositoryWithHabit(start = LocalDate.of(2026, 7, 1))

        assertEquals(
            ToggleResult.Checked,
            repository.toggle(1, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 30)),
        )
        assertEquals(
            ToggleResult.Unchecked,
            repository.toggle(1, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 30)),
        )
    }

    @Test
    fun archivedHabitRejectsCheckInOnItsArchivedDate() = runTest {
        val archivedDate = LocalDate.of(2026, 7, 20)
        val repository = repositoryWithHabit(
            start = LocalDate.of(2026, 7, 1),
            archivedEpochDay = archivedDate.toEpochDay(),
        )

        assertEquals(
            ToggleResult.Rejected("该日期不在习惯有效范围内"),
            repository.toggle(1, archivedDate, LocalDate.of(2026, 7, 30)),
        )
    }

    @Test
    fun missingHabitIsRejected() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        database = db
        val repository = RoomCheckInRepository(
            db,
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
        )

        assertEquals(
            ToggleResult.Rejected("习惯不存在"),
            repository.toggle(99, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 30)),
        )
    }

    @Test
    fun insertedCheckInUsesInjectedClockForBothAuditTimestamps() = runTest {
        val repository = repositoryWithHabit(start = LocalDate.of(2026, 7, 1))

        assertEquals(
            ToggleResult.Checked,
            repository.toggle(1, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 30)),
        )

        val checkIn = database!!.checkInDao().getForHabit(1).single()
        assertEquals(1000, checkIn.createdAt)
        assertEquals(1000, checkIn.updatedAt)
    }
}
