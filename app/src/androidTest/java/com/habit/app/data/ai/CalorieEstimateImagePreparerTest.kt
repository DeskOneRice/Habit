package com.habit.app.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalorieEstimateImagePreparerTest {
    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDirectory = File(context.cacheDir, "ai-image-preparer-test-${System.nanoTime()}").apply {
            check(mkdirs())
        }
    }

    @After
    fun tearDown() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun appliesExifOrientationBoundsLongestEdgeAndNeverChangesOriginal() = runBlocking {
        val original = File(testDirectory, "oriented-original.jpg")
        createExifOrientedFixture(original, width = 4000, height = 3000, orientation = 6)
        val originalDigest = sha256(original)

        val prepared = CalorieEstimateImagePreparer(File(testDirectory, "prepared"))
            .prepare(listOf(original))
            .single()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(prepared.file.path, bounds)
        assertEquals("image/jpeg", prepared.mimeType)
        assertTrue(bounds.outWidth > 0 && bounds.outHeight > 0)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 1536)
        assertTrue("EXIF orientation 6 must rotate landscape input to portrait output", bounds.outHeight > bounds.outWidth)
        assertNotEquals(original.canonicalPath, prepared.file.canonicalPath)
        assertEquals(originalDigest, sha256(original))

        val temporaryFile = prepared.file
        assertTrue(temporaryFile.isFile)
        prepared.close()
        assertFalse(temporaryFile.exists())
        assertTrue(original.isFile)
    }

    @Test
    fun threePreparedJpegsRemainUnderSixMiBAndAllTempsAreCloseable() = runBlocking {
        val original = File(testDirectory, "noisy-original.jpg")
        createNoisyFixture(original, width = 4000, height = 3000)
        val originalDigest = sha256(original)

        val prepared = CalorieEstimateImagePreparer(File(testDirectory, "prepared"))
            .prepare(listOf(original, original, original))

        assertEquals(3, prepared.size)
        assertTrue(prepared.sumOf { it.file.length() } <= MAX_PREPARED_AI_IMAGE_BYTES)
        prepared.forEach { image ->
            assertTrue(BitmapFactory.decodeFile(image.file.path) != null)
            assertTrue(image.asAiPreparedImage().bytes.contentEquals(image.file.readBytes()))
        }
        assertEquals(originalDigest, sha256(original))
        val tempFiles = prepared.map { it.file }
        prepared.forEach(PreparedAiImage::close)
        assertTrue(tempFiles.none(File::exists))
    }

    @Test
    fun cancellationDeletesPreparedFilesThatWereNeverDelivered() = runBlocking {
        val original = File(testDirectory, "cancel-original.jpg")
        createNoisyFixture(original, width = 4000, height = 3000)
        val preparedDirectory = File(testDirectory, "cancel-prepared")
        val preparer = CalorieEstimateImagePreparer(preparedDirectory)

        val call = launch(Dispatchers.Default) {
            preparer.prepare(listOf(original, original, original))
        }
        withTimeout(10_000) {
            while (preparedDirectory.listFiles().orEmpty().isEmpty()) delay(10)
        }
        assertTrue(call.isActive)
        call.cancelAndJoin()

        assertTrue(preparedDirectory.listFiles().orEmpty().isEmpty())
        assertTrue(original.isFile)
    }

    private fun createExifOrientedFixture(file: File, width: Int, height: Int, orientation: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff336699.toInt())
        val jpeg = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
            output.toByteArray()
        }
        bitmap.recycle()
        file.writeBytes(addExifOrientation(jpeg, orientation))
    }

    private fun createNoisyFixture(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        var state = 0x13579bdf
        for (y in 0 until height) {
            for (x in row.indices) {
                state = state * 1103515245 + 12345
                row[x] = 0xff000000.toInt() or (state and 0x00ffffff)
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
    }

    private fun addExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        require(jpeg.size >= 2 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
        val exifPayload = byteArrayOf(
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
            'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8,
            0, 1,
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
            0, 0, 0, 0,
        )
        val segmentLength = exifPayload.size + 2
        return ByteArrayOutputStream(jpeg.size + exifPayload.size + 4).use { output ->
            output.write(jpeg, 0, 2)
            output.write(0xff)
            output.write(0xe1)
            output.write(segmentLength ushr 8)
            output.write(segmentLength and 0xff)
            output.write(exifPayload)
            output.write(jpeg, 2, jpeg.size - 2)
            output.toByteArray()
        }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}
