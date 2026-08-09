package com.habit.app.ui.diet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DietPhotoPreviewDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val targetHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    var loadState by remember(file.path, file.lastModified()) {
        mutableStateOf<PhotoLoadState>(PhotoLoadState.Loading)
    }

    LaunchedEffect(file.path, file.lastModified(), targetWidth, targetHeight) {
        loadState = withContext(Dispatchers.IO) {
            decodePreview(file, targetWidth, targetHeight)
                ?.let(PhotoLoadState::Ready)
                ?: PhotoLoadState.Failed
        }
    }
    LaunchedEffect(loadState) {
        if (loadState == PhotoLoadState.Failed) onDismiss()
    }
    if (loadState == PhotoLoadState.Failed) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
                .testTag("diet_photo_preview"),
        ) {
            when (val state = loadState) {
                PhotoLoadState.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                PhotoLoadState.Failed -> Unit
                is PhotoLoadState.Ready -> Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "放大的饮食照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 64.dp)
                        .clickable(onClick = {}),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).size(48.dp),
            ) {
                Text("×", color = Color.White, fontSize = 32.sp)
            }
        }
    }
}

private sealed interface PhotoLoadState {
    data object Loading : PhotoLoadState
    data object Failed : PhotoLoadState
    data class Ready(val bitmap: Bitmap) : PhotoLoadState
}

private fun decodePreview(file: File, targetWidth: Int, targetHeight: Int): Bitmap? = runCatching {
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
