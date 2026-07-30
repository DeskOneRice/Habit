package com.habit.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.habit.app.domain.model.DaySnapshot
import com.habit.app.ui.components.HabitEmoji
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayTitleFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)
private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

@Composable
fun DayCheckInSheet(
    date: LocalDate,
    today: LocalDate,
    snapshot: DaySnapshot?,
    zoneId: ZoneId,
    togglingHabitIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val habits = snapshot?.takeIf { it.epochDay == date.toEpochDay() }?.habits.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day_checkin_sheet")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(date.format(dayTitleFormatter), style = MaterialTheme.typography.titleLarge)
            FilledIconButton(
                onClick = onClose,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("day_checkin_close")
                    .semantics { contentDescription = "关闭日期打卡面板" },
            ) {
                Text("×")
            }
        }
        Text("已完成 ${habits.count { it.checked }} / ${habits.size}")
        Text(
            "补签属于所选业务日期，不是操作发生的时间。",
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (habits.isEmpty()) {
            Text("这一天没有可用习惯", modifier = Modifier.padding(vertical = 20.dp))
        }
        habits.forEach { dayHabit ->
            val habit = dayHabit.habit
            ListItem(
                headlineContent = { Text(habit.name) },
                supportingContent = {
                    dayHabit.checkedAt?.let { timestamp ->
                        Text("完成于 ${formatTimestamp(timestamp, zoneId)}")
                    }
                },
                leadingContent = { HabitEmoji(habit.iconKey) },
                trailingContent = {
                    FilledIconButton(
                        onClick = { onToggle(habit.id) },
                        enabled = !date.isAfter(today) && habit.id !in togglingHabitIds,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("checkin_habit_${habit.id}")
                            .semantics {
                                contentDescription = if (dayHabit.checked) {
                                    "取消${habit.name}的打卡"
                                } else {
                                    "为${habit.name}补签"
                                }
                            },
                    ) {
                        Text(if (dayHabit.checked) "✓" else "○")
                    }
                },
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(timestamp).atZone(zoneId).format(timestampFormatter)
