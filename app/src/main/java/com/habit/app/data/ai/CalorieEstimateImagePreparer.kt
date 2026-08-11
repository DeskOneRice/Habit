package com.habit.app.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

const val MAX_PREPARED_AI_IMAGE_BYTES = 6L * 1024 * 1024

interface CalorieImagePreparer {
    suspend fun prepare(sourceFiles: List<File>): List<PreparedAiImage>
}

class PreparedAiImage internal constructor(
    val file: File,
    val mimeType: String = "image/jpeg",
    private val deleteFile: (File) -> Boolean = { it.delete() },
) : Closeable {
    fun asAiPreparedImage(): AiPreparedImage = AiPreparedImage(mimeType, file.readBytes())

    override fun close() {
        repeat(TEMP_DELETE_ATTEMPTS) {
            if (!file.exists() || deleteFile(file)) return
        }
        throw IOException("Unable to delete temporary AI image")
    }

    private companion object {
        const val TEMP_DELETE_ATTEMPTS = 3
    }
}

class CalorieEstimateImagePreparer internal constructor(
    private val temporaryDirectory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val afterTemporaryFileCreated: ((File) -> Unit)? = null,
    private val bitmapStageObserver: ((String, Bitmap) -> Unit)? = null,
    private val maxTotalBytes: Long = MAX_PREPARED_AI_IMAGE_BYTES,
    private val onPayloadRescale: (() -> Unit)? = null,
    private val encodeJpeg: (Bitmap, OutputStream) -> Boolean = { bitmap, output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    },
    private val ownerFactory: (File) -> PreparedAiImage = { PreparedAiImage(it) },
    private val rawFileDelete: (File) -> Boolean = { it.delete() },
) : CalorieImagePreparer {
    override suspend fun prepare(sourceFiles: List<File>): List<PreparedAiImage> {
        val prepared = mutableListOf<PreparedAiImage>()
        var delivered = false
        var pendingFailure: Throwable? = null
        try {
            val result = withContext(dispatcher) {
                require(sourceFiles.size in 1..3) { "AI image count must be between 1 and 3" }
                require(maxTotalBytes > 0) { "AI image payload limit must be positive" }
                check(temporaryDirectory.exists() || temporaryDirectory.mkdirs()) {
                    "Unable to create AI image temporary directory"
                }
                sourceFiles.forEach { source ->
                    currentCoroutineContext().ensureActive()
                    require(source.isFile) { "AI image source does not exist" }
                    val bitmap = decodeOrientedAndBounded(source)
                    try {
                        val output = File.createTempFile("habit-ai-", ".jpg", temporaryDirectory)
                        var owner: PreparedAiImage? = null
                        try {
                            afterTemporaryFileCreated?.invoke(output)
                            currentCoroutineContext().ensureActive()
                            owner = ownerFactory(output)
                            try {
                                prepared.add(owner)
                            } catch (failure: Throwable) {
                                closeAfterFailure(owner, failure)
                                throw failure
                            }
                            output.outputStream().buffered().use { stream ->
                                check(encodeJpeg(bitmap, stream)) {
                                    "Unable to encode AI image"
                                }
                            }
                        } catch (failure: Throwable) {
                            if (owner == null) {
                                val deleted = deleteRawBestEffort(output)
                                if (!deleted && failure !is OutOfMemoryError) {
                                    failure.addSuppressed(IOException("Unable to clean temporary AI image"))
                                }
                            }
                            throw failure
                        }
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
                enforceTotalPayloadLimit(prepared)
                currentCoroutineContext().ensureActive()
                prepared.toList()
            }
            delivered = true
            return result
        } catch (failure: Throwable) {
            pendingFailure = failure
            throw failure
        } finally {
            if (!delivered) cleanupUndelivered(prepared, pendingFailure)
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
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        var result: Bitmap? = null
        try {
            bitmapStageObserver?.invoke(BITMAP_STAGE_DECODED, decoded)
            oriented = applyExifOrientation(decoded, readExifOrientation(source))
            val longestEdge = max(oriented.width, oriented.height)
            result = if (longestEdge <= MAX_LONG_EDGE) {
                oriented
            } else {
                val scale = MAX_LONG_EDGE.toFloat() / longestEdge
                scaled = Bitmap.createScaledBitmap(
                    oriented,
                    max(1, (oriented.width * scale).roundToInt()),
                    max(1, (oriented.height * scale).roundToInt()),
                    true,
                )
                scaled
            }
            return requireNotNull(result)
        } finally {
            recycleUnlessReturned(scaled, result)
            if (oriented !== scaled) recycleUnlessReturned(oriented, result)
            if (decoded !== oriented && decoded !== scaled) recycleUnlessReturned(decoded, result)
        }
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
        while (total > maxTotalBytes) {
            onPayloadRescale?.invoke()
            val ratio = sqrt(maxTotalBytes.toDouble() / total) * PAYLOAD_HEADROOM
            val scale = ratio.coerceIn(MIN_RESCALE_FACTOR, MAX_RESCALE_FACTOR)
            images.forEach { image ->
                val bitmap = BitmapFactory.decodeFile(image.file.path)
                    ?: throw IllegalArgumentException("Unable to decode prepared AI image")
                var scaled: Bitmap? = null
                try {
                    scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        max(1, (bitmap.width * scale).roundToInt()),
                        max(1, (bitmap.height * scale).roundToInt()),
                        true,
                    )
                    image.file.outputStream().buffered().use { stream ->
                        check(encodeJpeg(requireNotNull(scaled), stream)) {
                            "Unable to re-encode AI image"
                        }
                    }
                } finally {
                    recycleUnlessReturned(scaled, null)
                    if (bitmap !== scaled) recycleUnlessReturned(bitmap, null)
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
        while (markerStart >= 0 && markerStart <= prefix.size - 4) {
            if (prefix.u8(markerStart) != 0xff) {
                markerStart++
                continue
            }
            val marker = prefix.u8(markerStart + 1)
            if (marker == 0xda || marker == 0xd9) break
            val segmentLength = prefix.u16(markerStart + 2, littleEndian = false) ?: break
            val segmentBase = markerStart + 2
            if (segmentLength < 2 || segmentBase > prefix.size - segmentLength) break
            if (marker == 0xe1) {
                parseExifOrientation(prefix, markerStart + 4, segmentLength - 2)?.let { return it }
            }
            markerStart += segmentLength + 2
        }
        return 1
    }

    private fun parseExifOrientation(bytes: ByteArray, payloadStart: Int, payloadLength: Int): Int? {
        if (payloadStart < 0 || payloadLength < 14 || payloadStart > bytes.size - payloadLength) return null
        if (!bytes.matches(payloadStart, EXIF_SIGNATURE)) return null
        val payloadEnd = payloadStart.toLong() + payloadLength.toLong()
        val tiffStart = payloadStart.toLong() + EXIF_SIGNATURE.size.toLong()
        if (!hasRange(tiffStart, 8, payloadEnd)) return null
        val littleEndian = when {
            bytes.u8(tiffStart) == 'I'.code && bytes.u8(tiffStart + 1) == 'I'.code -> true
            bytes.u8(tiffStart) == 'M'.code && bytes.u8(tiffStart + 1) == 'M'.code -> false
            else -> return null
        }
        if (bytes.u16(tiffStart + 2, littleEndian) != 42) return null
        val ifdOffset = bytes.u32(tiffStart + 4, littleEndian) ?: return null
        if (ifdOffset > payloadEnd - tiffStart) return null
        val ifdStart = tiffStart + ifdOffset
        if (!hasRange(ifdStart, 2, payloadEnd)) return null
        val entryCount = bytes.u16(ifdStart, littleEndian) ?: return null
        repeat(entryCount) { index ->
            val entry = ifdStart + 2L + index.toLong() * 12L
            if (!hasRange(entry, 12, payloadEnd)) return null
            if (bytes.u16(entry, littleEndian) == ORIENTATION_TAG &&
                bytes.u16(entry + 2, littleEndian) == SHORT_TYPE &&
                bytes.u32(entry + 4, littleEndian) == 1L
            ) {
                return bytes.u16(entry + 8, littleEndian).takeIf { it in 1..8 }
            }
        }
        return null
    }

    private fun ByteArray.u8(offset: Long): Int? =
        if (offset < 0 || offset >= size.toLong()) null else get(offset.toInt()).toInt() and 0xff

    private fun ByteArray.u8(offset: Int): Int = get(offset).toInt() and 0xff

    private fun ByteArray.u16(offset: Long, littleEndian: Boolean): Int? {
        if (!hasRange(offset, 2, size.toLong())) return null
        val first = u8(offset) ?: return null
        val second = u8(offset + 1) ?: return null
        return if (littleEndian) first or (second shl 8) else (first shl 8) or second
    }

    private fun ByteArray.u16(offset: Int, littleEndian: Boolean): Int? =
        u16(offset.toLong(), littleEndian)

    private fun ByteArray.u32(offset: Long, littleEndian: Boolean): Long? {
        if (!hasRange(offset, 4, size.toLong())) return null
        val value = if (littleEndian) {
            u8(offset)!!.toLong() or
                (u8(offset + 1)!!.toLong() shl 8) or
                (u8(offset + 2)!!.toLong() shl 16) or
                (u8(offset + 3)!!.toLong() shl 24)
        } else {
            (u8(offset)!!.toLong() shl 24) or
                (u8(offset + 1)!!.toLong() shl 16) or
                (u8(offset + 2)!!.toLong() shl 8) or
                u8(offset + 3)!!.toLong()
        }
        return value
    }

    private fun ByteArray.matches(offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && offset <= size - expected.size &&
            expected.indices.all { this[offset + it] == expected[it] }

    private fun hasRange(offset: Long, length: Int, endExclusive: Long): Boolean =
        offset >= 0 && length >= 0 && offset <= endExclusive && length.toLong() <= endExclusive - offset

    private fun recycleUnlessReturned(bitmap: Bitmap?, returned: Bitmap?) {
        if (bitmap != null && bitmap !== returned && !bitmap.isRecycled) bitmap.recycle()
    }

    private fun deleteRawBestEffort(file: File): Boolean {
        var attempts = 0
        while (attempts < RAW_TEMP_DELETE_ATTEMPTS) {
            attempts++
            try {
                if (!file.exists() || rawFileDelete(file)) return true
            } catch (_: Throwable) {
                // The original failure, including the exact OOM instance, always wins.
            }
        }
        return try {
            !file.exists()
        } catch (_: Throwable) {
            false
        }
    }

    private fun closeAfterFailure(image: PreparedAiImage, failure: Throwable) {
        try {
            image.close()
        } catch (_: IOException) {
            failure.addSuppressed(IOException("Unable to clean temporary AI image"))
        }
    }

    private fun cleanupUndelivered(images: List<PreparedAiImage>, pendingFailure: Throwable?) {
        var cleanupFailure: IOException? = null
        images.forEach { image ->
            try {
                image.close()
            } catch (_: IOException) {
                if (cleanupFailure == null) cleanupFailure = IOException("Unable to clean temporary AI image")
            }
        }
        cleanupFailure?.let { failure ->
            if (pendingFailure != null) pendingFailure.addSuppressed(failure) else throw failure
        }
    }

    private companion object {
        const val MAX_LONG_EDGE = 1536
        const val JPEG_QUALITY = 82
        const val MAX_EXIF_SCAN_BYTES = 256 * 1024
        const val PAYLOAD_HEADROOM = 0.95
        const val MIN_RESCALE_FACTOR = 0.25
        const val MAX_RESCALE_FACTOR = 0.9
        const val ORIENTATION_TAG = 0x0112
        const val SHORT_TYPE = 3
        const val BITMAP_STAGE_DECODED = "decoded"
        const val RAW_TEMP_DELETE_ATTEMPTS = 3
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
