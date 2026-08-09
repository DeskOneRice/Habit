package com.habit.app.ui.diet

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSamplingTest {
    @Test
    fun largePhotoUsesPowerOfTwoSampleForDisplayBounds() {
        assertEquals(4, calculateInSampleSize(8000, 6000, 1080, 2400))
    }

    @Test
    fun smallPhotoIsNotUpsampled() {
        assertEquals(1, calculateInSampleSize(800, 600, 1080, 2400))
    }
}
