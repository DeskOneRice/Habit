package com.habit.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.ui.components.DeviceDateRefreshEffect
import com.habit.app.ui.components.ArrowDirection
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.LightweightArrowButton
import com.habit.app.ui.components.NavigationMode
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onOpenDrawer: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetVisible by remember { mutableStateOf(false) }

    DeviceDateRefreshEffect(viewModel::refreshDeviceDate)

    Scaffold(
        topBar = {
            HabitTopAppBar("习惯日历", NavigationMode.MENU, onOpenDrawer)
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("calendar_screen")
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightweightArrowButton(
                    onClick = viewModel::previousMonth,
                    direction = ArrowDirection.PREVIOUS,
                    contentDescription = "上个月",
                    modifier = Modifier.testTag("calendar_previous_month"),
                )
                Text(
                    text = "${state.visibleMonth.year}年${state.visibleMonth.monthValue}月",
                    style = MaterialTheme.typography.headlineSmall,
                )
                LightweightArrowButton(
                    onClick = viewModel::nextMonth,
                    direction = ArrowDirection.NEXT,
                    contentDescription = "下个月",
                    modifier = Modifier.testTag("calendar_next_month"),
                )
            }
            state.month?.let { month ->
                MonthGrid(
                    snapshot = month,
                    selectedDate = state.selectedDate,
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                        sheetVisible = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                MonthSummary(
                    activeDays = month.stats.activeDays,
                    completionRate = month.stats.completionRate,
                    longestStreak = longestMonthScopedStreak(month.marksByEpochDay.keys),
                )
            }
            state.message?.let { Text(it, Modifier.testTag("calendar_message")) }
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(onDismissRequest = { sheetVisible = false }) {
            DayCheckInSheet(
                date = state.selectedDate,
                today = state.today,
                snapshot = state.day,
                zoneId = state.zoneId,
                togglingHabitIds = state.togglingHabitIds,
                onToggle = viewModel::toggle,
                onClose = { sheetVisible = false },
            )
        }
    }
}

@Composable
private fun MonthSummary(
    activeDays: Int,
    completionRate: Float,
    longestStreak: Int,
) {
    HabitCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .testTag("calendar_summary"),
    ) {
        Text("本月概览", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        val metrics = listOf(
            "$activeDays" to "活跃天数",
            "${(completionRate * 100).roundToInt()}%" to "完成率",
            "$longestStreak" to "最长连续",
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            metrics.forEachIndexed { index, metric ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(metric.first, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(metric.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index < metrics.lastIndex) {
                    androidx.compose.material3.VerticalDivider(Modifier.height(38.dp))
                }
            }
        }
    }
}

internal fun longestMonthScopedStreak(epochDays: Set<Long>): Int {
    var longest = 0
    var current = 0
    var previous: Long? = null
    epochDays.sorted().forEach { epochDay ->
        current = if (previous?.let { epochDay == it + 1 } == true) current + 1 else 1
        longest = maxOf(longest, current)
        previous = epochDay
    }
    return longest
}
