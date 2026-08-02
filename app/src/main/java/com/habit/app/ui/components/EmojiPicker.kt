package com.habit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit.app.ui.emoji.EmojiCatalog
import com.habit.app.ui.emoji.EmojiCategory
import com.habit.app.ui.emoji.normalizeEmojiKey

private data class PickerEmoji(val key: String, val emoji: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPicker(
    selected: String,
    recentKeys: List<String> = emptyList(),
    onSelected: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { visible = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_emoji_picker"),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(habitEmoji(selected), fontSize = 26.sp)
        Text("  更换习惯图标")
    }
    if (visible) {
        EmojiPickerSheet(
            selected = selected,
            recentKeys = recentKeys,
            onSelected = {
                onSelected(it)
                visible = false
            },
            onDismiss = { visible = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerSheet(
    selected: String,
    recentKeys: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if (recentKeys.isEmpty()) EmojiCategory.STUDY else EmojiCategory.RECENT) }
    var customInput by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    val visibleOptions = if (category == EmojiCategory.RECENT) {
        recentKeys.map { key ->
            val catalog = EmojiCatalog.options.firstOrNull { it.key == key }
            PickerEmoji(key, habitEmoji(key), catalog?.label ?: "最近使用")
        }.filter { option -> query.isBlank() || option.label.contains(query.trim(), ignoreCase = true) }
    } else {
        EmojiCatalog.search(query, category).map { PickerEmoji(it.key, it.emoji, it.label) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("emoji_picker_sheet")) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选择一个习惯图标", style = MaterialTheme.typography.titleLarge)
                    Text("搜索或使用手机键盘输入", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onDismiss) { Text("关闭") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag("emoji_search"),
                label = { Text("搜索：实验、羽毛球、喝水…") },
                singleLine = true,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EmojiCategory.entries) { item ->
                    if (item != EmojiCategory.RECENT || recentKeys.isNotEmpty()) {
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.label) },
                            modifier = Modifier.semantics { this.selected = category == item },
                        )
                    }
                }
            }
            if (visibleOptions.isEmpty()) {
                Text("没有匹配图标，可以在下方自定义。", modifier = Modifier.padding(vertical = 18.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleOptions, key = PickerEmoji::key) { option ->
                        OutlinedButton(
                            onClick = { onSelected(option.key) },
                            modifier = Modifier
                                .size(52.dp)
                                .semantics {
                                    contentDescription = "习惯图标：${option.label}"
                                    this.selected = selected == option.key
                                }
                                .testTag("emoji_${option.key}"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text(option.emoji, fontSize = 22.sp) }
                    }
                }
            }
            Text("自定义 Emoji", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it; customError = null },
                    modifier = Modifier.weight(1f).testTag("custom_emoji_input"),
                    label = { Text("输入一个 Emoji") },
                    isError = customError != null,
                    supportingText = customError?.let { message -> { Text(message) } },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val normalized = normalizeEmojiKey(customInput)
                        if (normalized == null) customError = "请输入一个 Emoji" else onSelected(normalized)
                    },
                    modifier = Modifier.heightIn(min = 56.dp).testTag("use_custom_emoji"),
                ) { Text("使用") }
            }
        }
    }
}
