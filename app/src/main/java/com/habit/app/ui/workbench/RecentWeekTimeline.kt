package com.habit.app.ui.workbench

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habit.app.ui.components.habitEmoji
import java.time.LocalDate

data class RecentWeekDisplay(
    val primaryEmoji: String?,
    val secondaryEmoji: String?,
    val overflowCount: Int?,
)

fun buildRecentWeekDisplay(iconKeys: List<String>): RecentWeekDisplay = when (iconKeys.size) {
    0 -> RecentWeekDisplay(null, null, null)
    1 -> RecentWeekDisplay(iconKeys[0], null, null)
    2 -> RecentWeekDisplay(iconKeys[0], iconKeys[1], null)
    else -> RecentWeekDisplay(iconKeys[0], null, iconKeys.size - 1)
}

@Composable
fun RecentWeekTimeline(
    days: List<RecentDay>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().testTag("recent_week_timeline")) {
        days.forEach { day ->
            RecentDayColumn(
                day = day,
                today = today,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecentDayColumn(
    day: RecentDay,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val display = buildRecentWeekDisplay(day.iconKeys)
    val isToday = day.date == today
    val outlineShape = RoundedCornerShape(13.dp)
    val dateBlockModifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .padding(horizontal = 2.dp)
        .then(
            if (isToday) {
                Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                    shape = outlineShape,
                )
            } else {
                Modifier
            },
        )

    Column(
        modifier = modifier
            .testTag("recent_day_${day.date}")
            .semantics {
                contentDescription = "${day.date.monthValue}月${day.date.dayOfMonth}日，完成${day.iconKeys.size}项"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = dateBlockModifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.date.weekdayLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        RecentItems(display)
    }
}

@Composable
private fun RecentItems(display: RecentWeekDisplay) {
    Row(
        modifier = Modifier.fillMaxWidth().height(26.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        display.primaryEmoji?.let { key ->
            RecentItemText(habitEmoji(key))
        }
        display.secondaryEmoji?.let { key ->
            RecentItemText(habitEmoji(key))
        }
        display.overflowCount?.let { count ->
            RecentItemText("+$count")
        }
    }
}

@Composable
private fun RowScope.RecentItemText(text: String) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LocalDate.weekdayLabel(): String = when (dayOfWeek.value) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}
