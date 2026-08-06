package com.habit.app.ui.diet

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType

@Composable
fun DietRecordThumbnail(
    record: MealRecord,
    photoStore: DietPhotoStore,
    modifier: Modifier = Modifier,
) {
    val firstPhoto = record.photos.minByOrNull { it.sortOrder }
    val photoFile = remember(firstPhoto?.relativePath, photoStore) {
        firstPhoto?.relativePath?.let { path -> runCatching { photoStore.file(path) }.getOrNull() }
    }
    val bitmap = remember(photoFile?.path, photoFile?.lastModified()) {
        photoFile?.takeIf { it.isFile }?.let { file ->
            runCatching {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "饮食照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp),
            )
        } else {
            Text(
                text = defaultDietEmoji(record),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { contentDescription = "默认饮食图标" },
            )
        }
    }
}

internal fun defaultDietEmoji(record: MealRecord): String = when {
    record.recordType == DietRecordType.BEVERAGE -> "☕"
    record.mealType == MealType.BREAKFAST -> "🥣"
    record.mealType == MealType.LUNCH -> "🍱"
    record.mealType == MealType.DINNER -> "🍲"
    record.mealType == MealType.LATE_NIGHT -> "🌙"
    else -> "🍎"
}
