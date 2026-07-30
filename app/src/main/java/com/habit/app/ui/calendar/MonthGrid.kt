package com.habit.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit.app.domain.model.CalendarMark
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.ui.components.habitEmoji
import java.time.DayOfWeek
import java.time.LocalDate

private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun MonthGrid(
    snapshot: MonthSnapshot,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val leadingEmptyCells = snapshot.month.atDay(1).dayOfWeek.mondayIndex()
        val cells = buildList<LocalDate?> {
            repeat(leadingEmptyCells) { add(null) }
            repeat(snapshot.month.lengthOfMonth()) { day -> add(snapshot.month.atDay(day + 1)) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(350.dp),
            userScrollEnabled = false,
        ) {
            items(cells) { date ->
                if (date == null) {
                    Box(Modifier.aspectRatio(1f))
                } else {
                    DayCell(
                        date = date,
                        marks = snapshot.marksByEpochDay[date.toEpochDay()].orEmpty(),
                        selected = date == selectedDate,
                        onClick = { onDateSelected(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    marks: List<CalendarMark>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val sortedMarks = marks.sortedWith(compareBy(CalendarMark::sortOrder, CalendarMark::habitId))
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .testTag("day_$date")
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            sortedMarks.take(4).forEachIndexed { index, mark ->
                Text(
                    text = habitEmoji(mark.iconKey),
                    fontSize = 10.sp,
                    modifier = Modifier.testTag("day_${date}_mark_$index"),
                )
            }
        }
        val overflow = (sortedMarks.size - 4).coerceAtLeast(0)
        if (overflow > 0) {
            Text(
                text = "+$overflow",
                fontSize = 9.sp,
                modifier = Modifier.testTag("day_${date}_overflow"),
            )
        }
    }
}

private fun DayOfWeek.mondayIndex(): Int = value - DayOfWeek.MONDAY.value
