package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.domain.model.HabitDraft
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHabitRepositoryTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC)
    private var database: HabitDatabase? = null

    @After
    fun close() {
        database?.close()
    }

    @Test
    fun createTrimsNameAssignsNextSortOrderAndUsesAuditClock() = runTest {
        val db = database()
        val categoryId = insertCategory(db)
        val repository = RoomHabitRepository(db.habitDao(), clock)

        val firstId = repository.create(draft(categoryId, "  First  "))
        val secondId = repository.create(draft(categoryId, "Second"))

        val first = db.habitDao().getById(firstId)!!
        val second = db.habitDao().getById(secondId)!!
        assertEquals("First", first.name)
        assertEquals(0, first.sortOrder)
        assertEquals(1, second.sortOrder)
        assertEquals(1234, first.createdAt)
        assertEquals(1234, first.updatedAt)
    }

    @Test
    fun createRejectsBlankOrTooLongNames() = runTest {
        val db = database()
        val repository = RoomHabitRepository(db.habitDao(), clock)
        val categoryId = insertCategory(db)

        val blank = runCatching { repository.create(draft(categoryId, "   ")) }.exceptionOrNull()
        val long = runCatching { repository.create(draft(categoryId, "a".repeat(31))) }.exceptionOrNull()

        assertEquals("请输入 1～30 个字符的习惯名称", blank?.message)
        assertEquals("请输入 1～30 个字符的习惯名称", long?.message)
    }

    @Test
    fun observeByIdEmitsCreatedHabitAndNullAfterDelete() = runTest {
        val db = database()
        val repository = RoomHabitRepository(db.habitDao(), clock)
        val id = repository.create(draft(insertCategory(db), "First"))

        assertEquals("First", repository.observeById(id).first()?.name)
        repository.delete(id)
        assertNull(repository.observeById(id).first())
    }

    @Test
    fun updateAndArchiveUseTheInjectedAuditClock() = runTest {
        val db = database()
        val categoryId = insertCategory(db)
        val id = RoomHabitRepository(
            db.habitDao(),
            Clock.fixed(Instant.ofEpochMilli(100), ZoneOffset.UTC),
        ).create(draft(categoryId, "First"))
        val repository = RoomHabitRepository(
            db.habitDao(),
            Clock.fixed(Instant.ofEpochMilli(2000), ZoneOffset.UTC),
        )

        repository.update(id, draft(categoryId, "Updated"))
        repository.archive(id, LocalDate.of(2026, 8, 1).toEpochDay())

        val habit = db.habitDao().getById(id)!!
        assertEquals("Updated", habit.name)
        assertEquals(2000, habit.updatedAt)
        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), habit.archivedEpochDay)
    }

    private fun database(): HabitDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        HabitDatabase::class.java,
    ).allowMainThreadQueries().build().also { database = it }

    private suspend fun insertCategory(database: HabitDatabase): Long = database.categoryDao().insert(
        CategoryEntity(
            name = "Study",
            isPreset = false,
            isHidden = false,
            sortOrder = 0,
            createdAt = 100,
            updatedAt = 100,
        ),
    )

    private fun draft(categoryId: Long, name: String) = HabitDraft(
        name = name,
        iconKey = "book",
        themeColor = 0xFF8DB9CC,
        categoryId = categoryId,
        startEpochDay = LocalDate.of(2026, 7, 30).toEpochDay(),
    )
}
