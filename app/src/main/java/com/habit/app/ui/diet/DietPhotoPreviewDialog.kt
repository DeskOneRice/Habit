package com.habit.app.ui.diet

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

@Composable
fun DietPhotoPreviewDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val targetHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val bitmapState = rememberDietSampledBitmap(file, targetWidth, targetHeight)
    LaunchedEffect(bitmapState) {
        if (bitmapState == DietSampledBitmapState.Failed) onDismiss()
    }
    if (bitmapState == DietSampledBitmapState.Failed) return

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
            when (bitmapState) {
                DietSampledBitmapState.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                DietSampledBitmapState.Failed -> Unit
                is DietSampledBitmapState.Ready -> Image(
                    bitmap = bitmapState.bitmap.asImageBitmap(),
                    contentDescription = "放大的饮食照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 64.dp)
                        .clickable(onClick = {})
                        .testTag("diet_photo_preview_${file.name}"),
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
