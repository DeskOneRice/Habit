package com.habit.app.ui.diet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface DietSampledBitmapState {
    data object Loading : DietSampledBitmapState
    data object Failed : DietSampledBitmapState
    data class Ready(val bitmap: Bitmap) : DietSampledBitmapState
}

@Composable
internal fun rememberDietSampledBitmap(
    file: File?,
    targetWidth: Int,
    targetHeight: Int,
): DietSampledBitmapState = key(
    file?.path,
    file?.lastModified(),
    targetWidth,
    targetHeight,
) {
    val state by produceState<DietSampledBitmapState>(
        initialValue = DietSampledBitmapState.Loading,
    ) {
        val ownedBitmap = AtomicReference<Bitmap?>(null)
        try {
            val decoded = withContext(Dispatchers.IO) {
                file?.let { decodeDietSampledBitmap(it, targetWidth, targetHeight) }
                    ?.also(ownedBitmap::set)
            }
            value = decoded?.let(DietSampledBitmapState::Ready) ?: DietSampledBitmapState.Failed
            awaitDispose {}
        } finally {
            ownedBitmap.getAndSet(null)?.takeUnless(Bitmap::isRecycled)?.recycle()
        }
    }
    state
}

private fun decodeDietSampledBitmap(
    file: File,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap? = runCatching {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    BitmapFactory.decodeFile(
        file.path,
        BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                targetWidth,
                targetHeight,
            )
            inPreferredConfig = Bitmap.Config.RGB_565
        },
    )
}.getOrNull()
