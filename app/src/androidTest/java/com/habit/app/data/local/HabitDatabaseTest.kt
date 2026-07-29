package com.habit.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDatabaseTest {
    private lateinit var db: HabitDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private suspend fun insertHabit(db: HabitDatabase): Long {
        val categoryId = db.categoryDao().insert(
            CategoryEntity(
                name = "学习",
                isPreset = false,
                isHidden = false,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        return db.habitDao().insert(
            HabitEntity(
                name = "背单词",
                iconKey = "book",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = 20650,
                archivedEpochDay = null,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
    }

    @Test
    fun duplicateCheckInForSameHabitAndDayIsRejected() = runTest {
        val habitId = insertHabit(db)
        val checkIn = CheckInEntity(0, habitId, 20664, 100, 100)

        db.checkInDao().insert(checkIn)

        val exception = try {
            db.checkInDao().insert(checkIn)
            null
        } catch (exception: SQLiteConstraintException) {
            exception
        }
        assertNotNull(exception)
    }

    @Test
    fun deletingHabitCascadesCheckIns() = runTest {
        val habitId = insertHabit(db)
        db.checkInDao().insert(CheckInEntity(0, habitId, 20664, 100, 100))

        db.habitDao().deleteById(habitId)

        assertTrue(db.checkInDao().getForHabit(habitId).isEmpty())
    }
}
