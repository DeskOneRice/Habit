package com.habit.app.ui.diet

import java.time.LocalDate
import java.time.LocalTime
import com.habit.app.domain.model.DietPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DietEditorViewModelTest {
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
