package com.habit.app.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

const val MAX_PREPARED_AI_IMAGE_BYTES = 6L * 1024 * 1024

class PreparedAiImage(
    val file: File,
    val mimeType: String = "image/jpeg",
) : Closeable {
    fun asAiPreparedImage(): AiPreparedImage = AiPreparedImage(mimeType, file.readBytes())

    override fun close() {
        if (file.exists() && !file.delete()) {
            throw IOException("Unable to delete temporary AI image")
        }
    }
}

class CalorieEstimateImagePreparer(
    private val temporaryDirectory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun prepare(sourceFiles: List<File>): List<PreparedAiImage> {
        val prepared = mutableListOf<PreparedAiImage>()
        var delivered = false
        try {
            val result = withContext(dispatcher) {
                require(sourceFiles.size in 1..3) { "AI image count must be between 1 and 3" }
                check(temporaryDirectory.exists() || temporaryDirectory.mkdirs()) {
                    "Unable to create AI image temporary directory"
                }
                sourceFiles.forEach { source ->
                    currentCoroutineContext().ensureActive()
                    require(source.isFile) { "AI image source does not exist" }
                    val bitmap = decodeOrientedAndBounded(source)
                    val output = File.createTempFile("habit-ai-", ".jpg", temporaryDirectory)
                    try {
                        output.outputStream().buffered().use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                                "Unable to encode AI image"
                            }
                        }
                    } catch (failure: Throwable) {
                        output.delete()
                        throw failure
                    } finally {
                        bitmap.recycle()
                    }
                    prepared += PreparedAiImage(output)
                }
                enforceTotalPayloadLimit(prepared)
                currentCoroutineContext().ensureActive()
                prepared.toList()
            }
            delivered = true
            return result
        } finally {
            if (!delivered) prepared.forEach { runCatching { it.close() } }
        }
    }

    private fun decodeOrientedAndBounded(source: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to decode AI image" }

        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_LONG_EDGE * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: throw IllegalArgumentException("Unable to decode AI image")

        val oriented = applyExifOrientation(decoded, readExifOrientation(source))
        if (oriented !== decoded) decoded.recycle()
        val longestEdge = max(oriented.width, oriented.height)
        if (longestEdge <= MAX_LONG_EDGE) return oriented

        val scale = MAX_LONG_EDGE.toFloat() / longestEdge
        val scaled = Bitmap.createScaledBitmap(
            oriented,
            max(1, (oriented.width * scale).roundToInt()),
            max(1, (oriented.height * scale).roundToInt()),
            true,
        )
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == 1) return bitmap
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                6 -> setRotate(90f)
                7 -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                8 -> setRotate(-90f)
                else -> return bitmap
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun enforceTotalPayloadLimit(images: List<PreparedAiImage>) {
        var total = images.sumOf { it.file.length() }
        while (total > MAX_PREPARED_AI_IMAGE_BYTES) {
            val ratio = sqrt(MAX_PREPARED_AI_IMAGE_BYTES.toDouble() / total) * PAYLOAD_HEADROOM
            val scale = ratio.coerceIn(MIN_RESCALE_FACTOR, MAX_RESCALE_FACTOR)
            images.forEach { image ->
                val bitmap = BitmapFactory.decodeFile(image.file.path)
                    ?: throw IllegalArgumentException("Unable to decode prepared AI image")
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    max(1, (bitmap.width * scale).roundToInt()),
                    max(1, (bitmap.height * scale).roundToInt()),
                    true,
                )
                try {
                    image.file.outputStream().buffered().use { stream ->
                        check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                            "Unable to re-encode AI image"
                        }
                    }
                } finally {
                    if (scaled !== bitmap) bitmap.recycle()
                    scaled.recycle()
                }
            }
            val reducedTotal = images.sumOf { it.file.length() }
            check(reducedTotal < total) { "Unable to reduce AI image payload" }
            total = reducedTotal
        }
    }

    private fun readExifOrientation(file: File): Int {
        val prefix = file.inputStream().buffered().use { input ->
            val output = ByteArray(MAX_EXIF_SCAN_BYTES)
            var total = 0
            while (total < output.size) {
                val count = input.read(output, total, output.size - total)
                if (count < 0) break
                total += count
            }
            output.copyOf(total)
        }
        if (prefix.size < 4 || prefix.u8(0) != 0xff || prefix.u8(1) != 0xd8) return 1
        var markerStart = 2
        while (markerStart + 4 <= prefix.size) {
            if (prefix.u8(markerStart) != 0xff) {
                markerStart++
                continue
            }
            val marker = prefix.u8(markerStart + 1)
            if (marker == 0xda || marker == 0xd9) break
            val segmentLength = prefix.u16BigEndian(markerStart + 2)
            if (segmentLength < 2 || markerStart + 2 + segmentLength > prefix.size) break
            if (marker == 0xe1) {
                parseExifOrientation(prefix, markerStart + 4, segmentLength - 2)?.let { return it }
            }
            markerStart += segmentLength + 2
        }
        return 1
    }

    private fun parseExifOrientation(bytes: ByteArray, payloadStart: Int, payloadLength: Int): Int? {
        if (payloadLength < 14 || payloadStart + payloadLength > bytes.size) return null
        if (!bytes.matches(payloadStart, EXIF_SIGNATURE)) return null
        val tiffStart = payloadStart + EXIF_SIGNATURE.size
        val littleEndian = when {
            bytes.u8(tiffStart) == 'I'.code && bytes.u8(tiffStart + 1) == 'I'.code -> true
            bytes.u8(tiffStart) == 'M'.code && bytes.u8(tiffStart + 1) == 'M'.code -> false
            else -> return null
        }
        if (bytes.u16(tiffStart + 2, littleEndian) != 42) return null
        val ifdOffset = bytes.u32(tiffStart + 4, littleEndian) ?: return null
        val ifdStart = tiffStart + ifdOffset
        if (ifdStart + 2 > payloadStart + payloadLength) return null
        val entryCount = bytes.u16(ifdStart, littleEndian)
        repeat(entryCount) { index ->
            val entry = ifdStart + 2 + index * 12
            if (entry + 12 > payloadStart + payloadLength) return null
            if (bytes.u16(entry, littleEndian) == ORIENTATION_TAG &&
                bytes.u16(entry + 2, littleEndian) == SHORT_TYPE &&
                bytes.u32(entry + 4, littleEndian) == 1
            ) {
                return bytes.u16(entry + 8, littleEndian).takeIf { it in 1..8 }
            }
        }
        return null
    }

    private fun ByteArray.u8(offset: Int): Int = get(offset).toInt() and 0xff

    private fun ByteArray.u16BigEndian(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

    private fun ByteArray.u16(offset: Int, littleEndian: Boolean): Int = if (littleEndian) {
        u8(offset) or (u8(offset + 1) shl 8)
    } else {
        (u8(offset) shl 8) or u8(offset + 1)
    }

    private fun ByteArray.u32(offset: Int, littleEndian: Boolean): Int? {
        if (offset < 0 || offset + 4 > size) return null
        val value = if (littleEndian) {
            u8(offset).toLong() or
                (u8(offset + 1).toLong() shl 8) or
                (u8(offset + 2).toLong() shl 16) or
                (u8(offset + 3).toLong() shl 24)
        } else {
            (u8(offset).toLong() shl 24) or
                (u8(offset + 1).toLong() shl 16) or
                (u8(offset + 2).toLong() shl 8) or
                u8(offset + 3).toLong()
        }
        return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun ByteArray.matches(offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && offset + expected.size <= size &&
            expected.indices.all { this[offset + it] == expected[it] }

    private companion object {
        const val MAX_LONG_EDGE = 1536
        const val JPEG_QUALITY = 82
        const val MAX_EXIF_SCAN_BYTES = 256 * 1024
        const val PAYLOAD_HEADROOM = 0.95
        const val MIN_RESCALE_FACTOR = 0.25
        const val MAX_RESCALE_FACTOR = 0.9
        const val ORIENTATION_TAG = 0x0112
        const val SHORT_TYPE = 3
        val EXIF_SIGNATURE = byteArrayOf(
            'E'.code.toByte(),
            'x'.code.toByte(),
            'i'.code.toByte(),
            'f'.code.toByte(),
            0,
            0,
        )
    }
}
