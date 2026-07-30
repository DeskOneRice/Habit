package com.habit.app.ui.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class EmojiOption(val key: String, val label: String)

private val emojiOptions = listOf(
    EmojiOption("book", "书本"),
    EmojiOption("sprout", "幼苗"),
    EmojiOption("run", "跑步"),
    EmojiOption("heart", "爱心"),
    EmojiOption("water", "喝水"),
    EmojiOption("star", "星星"),
)

@Composable fun EmojiPicker(selected: String, onSelected: (String) -> Unit) = FlowRow(Modifier.fillMaxWidth()) {
    emojiOptions.forEach { option ->
        FilterChip(
            selected = selected == option.key,
            onClick = { onSelected(option.key) },
            label = { Text(habitEmoji(option.key)) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "习惯图标：${option.label}" }
                .testTag("emoji_${option.key}"),
        )
    }
}
