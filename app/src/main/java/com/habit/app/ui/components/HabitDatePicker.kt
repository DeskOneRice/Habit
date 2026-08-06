package com.habit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

val HABIT_WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
internal val HABIT_CALENDAR_CELL_ALIGNMENT = Alignment.Center

fun buildMonthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    return List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
}

@Composable
fun HabitDatePickerDialog(
    selectedDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var visibleMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    var draftDate by remember(selectedDate) { mutableStateOf(selectedDate) }
    val cells = buildMonthCells(visibleMonth).let { values ->
        values + List((7 - values.size % 7) % 7) { null }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("habit_date_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择开始日期", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${draftDate.year}年${draftDate.monthValue}月${draftDate.dayOfMonth}日",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightweightArrowButton(
                        onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                        direction = ArrowDirection.PREVIOUS,
                        contentDescription = "上个月",
                    )
                    Text("${visibleMonth.year}年${visibleMonth.monthValue}月")
                    LightweightArrowButton(
                        onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                        direction = ArrowDirection.NEXT,
                        contentDescription = "下个月",
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    HABIT_WEEKDAY_LABELS.forEachIndexed { index, label ->
                        CalendarCell(tag = "habit_weekday_${index + 1}", square = false) {
                            Text(
                                label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                                CalendarCell {}
                            } else {
                                val selected = date == draftDate
                                CalendarCell(tag = "habit_date_${date.dayOfMonth}") {
                                    TextButton(
                                        onClick = { draftDate = date },
                                        modifier = Modifier.fillMaxSize(),
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    ) { Text(date.dayOfMonth.toString()) }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(draftDate) }) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalendarCell(
    tag: String? = null,
    square: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val modifier = Modifier
        .weight(1f)
        .let { base -> if (square) base.aspectRatio(1f) else base.height(40.dp) }
        .let { base -> if (tag == null) base else base.testTag(tag) }
    Box(
        modifier = modifier,
        contentAlignment = HABIT_CALENDAR_CELL_ALIGNMENT,
        content = content,
    )
}
