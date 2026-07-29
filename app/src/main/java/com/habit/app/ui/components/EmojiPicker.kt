package com.habit.app.ui.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

private val emojiKeys = listOf("book", "sprout", "run", "heart", "water", "star")

@Composable fun EmojiPicker(selected: String, onSelected: (String) -> Unit) = FlowRow(Modifier.fillMaxWidth()) {
    emojiKeys.forEach { key ->
        FilterChip(
            selected = selected == key,
            onClick = { onSelected(key) },
            label = { Text(habitEmoji(key)) },
            modifier = Modifier.testTag("emoji_$key"),
        )
    }
}
