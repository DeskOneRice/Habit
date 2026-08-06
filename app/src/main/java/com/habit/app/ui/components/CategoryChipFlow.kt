package com.habit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class CategoryChipItem(
    val id: Long,
    val name: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChipFlow(
    items: List<CategoryChipItem>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
    onManage: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.testTag("category_chip_flow"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item.id == selectedId,
                onClick = { onSelected(item.id) },
                label = { Text(item.name) },
                modifier = Modifier.testTag("category_chip_${item.id}"),
            )
        }
        onCreate?.let { create ->
            TextButton(onClick = create, modifier = Modifier.testTag("category_quick_create")) {
                Text("＋ 新建")
            }
        }
        onManage?.let { manage ->
            TextButton(onClick = manage, modifier = Modifier.testTag("category_manage")) {
                Text("管理分类")
            }
        }
    }
}
