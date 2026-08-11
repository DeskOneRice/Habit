package com.habit.app.ui.diet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.displayTitle
import com.habit.app.domain.model.displayType
import com.habit.app.domain.time.HabitTimePolicy
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitAddFab
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode
import com.habit.app.ui.components.HabitPagerTab
import com.habit.app.ui.components.HabitPagerTabStrip
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun DietDiaryScreen(
    viewModel: DietDiaryViewModel,
    photoStore: DietPhotoStore,
    onOpenDrawer: () -> Unit,
    onAdd: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onRepeatRecord: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = if (state.mode == DietDiaryMode.RECENT) 0 else 1,
        pageCount = { 2 },
    )
    var previewFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(state.mode) {
        val target = if (state.mode == DietDiaryMode.RECENT) 0 else 1
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.selectMode(if (page == 0) DietDiaryMode.RECENT else DietDiaryMode.DAY)
            }
    }

    Scaffold(
        topBar = { HabitTopAppBar("饮食日记", NavigationMode.MENU, onOpenDrawer) },
        floatingActionButton = {
            HabitAddFab(onClick = onAdd, testTag = "diet_add")
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("diet_diary"),
        ) {
            DiaryModeSelector(
                mode = state.mode,
                onSelected = { selected ->
                    viewModel.selectMode(selected)
                    scope.launch {
                        pagerState.animateScrollToPage(if (selected == DietDiaryMode.RECENT) 0 else 1)
                    }
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (page == 0) {
                        if (!state.isLoading && state.recentGroups.isEmpty()) {
                            item(key = "recent_empty") {
                                EmptyDiaryMessage("还没有饮食记录\n点右下角开始记一餐")
                            }
                        }
                        state.recentGroups.forEach { group ->
                            item(key = "date_${group.epochDay}") {
                                Text(
                                    LocalDate.ofEpochDay(group.epochDay).format(DATE_HEADER_FORMAT),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            items(group.records, key = { "recent_${it.id}" }) { record ->
                                MealRecordCard(
                                    record,
                                    state.categoryNames[record.dietCategoryId].orEmpty(),
                                    photoStore,
                                    onOpenRecord,
                                    onRepeatRecord,
                                    onPhotoPreview = { previewFile = it },
                                )
                            }
                        }
                    } else {
                        item(key = "week") {
                            DiaryWeekSelector(state.selectedDate, viewModel::selectDate)
                        }
                        item(key = "summary") {
                            DiaryDaySummary(state)
                        }
                        if (!state.isLoading && state.records.isEmpty()) {
                            item(key = "day_empty") {
                                EmptyDiaryMessage("今天还没有记录\n点右下角开始记一餐")
                            }
                        }
                        items(state.records, key = { "day_${it.id}" }) { record ->
                            MealRecordCard(
                                record,
                                state.categoryNames[record.dietCategoryId].orEmpty(),
                                photoStore,
                                onOpenRecord,
                                onRepeatRecord,
                                onPhotoPreview = { previewFile = it },
                            )
                        }
                    }
                }
            }
        }
    }
    previewFile?.let { file -> DietPhotoPreviewDialog(file) { previewFile = null } }
}

@Composable
private fun DiaryModeSelector(mode: DietDiaryMode, onSelected: (DietDiaryMode) -> Unit) {
    val modes = listOf(DietDiaryMode.RECENT, DietDiaryMode.DAY)
    HabitPagerTabStrip(
        tabs = listOf(
            HabitPagerTab("最近记录", "diet_mode_recent"),
            HabitPagerTab("按日查看", "diet_mode_day"),
        ),
        selectedIndex = modes.indexOf(mode).coerceAtLeast(0),
        onSelected = { onSelected(modes[it]) },
    )
}

@Composable
private fun DiaryWeekSelector(selectedDate: LocalDate, onSelected: (LocalDate) -> Unit) {
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val selected = date == selectedDate
            Surface(
                onClick = { onSelected(date) },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp).testTag("diet_day_${date.toEpochDay()}"),
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(date.dayOfWeek.displayName(), style = MaterialTheme.typography.labelMedium)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DiaryDaySummary(state: DietDiaryUiState) {
    HabitCard(Modifier.fillMaxWidth()) {
        Text(state.selectedDate.format(DateTimeFormatter.ofPattern("M 月 d 日")), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${state.records.size} 条记录", style = MaterialTheme.typography.headlineSmall)
                Text("餐饮记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(state.totalCalories?.let { "$it kcal" } ?: "未记录热量", style = MaterialTheme.typography.headlineSmall)
                if (state.beverageCups > 0) Text("饮品 ${state.beverageCups} 杯", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyDiaryMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MealRecordCard(
    record: MealRecord,
    categoryName: String,
    photoStore: DietPhotoStore,
    onOpen: (Long) -> Unit,
    onRepeat: (Long) -> Unit,
    onPhotoPreview: (File) -> Unit,
) {
    HabitCard(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(record.id) }
            .testTag("diet_record_${record.id}"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DietRecordThumbnail(record, photoStore, onPhotoClick = onPhotoPreview)
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 112.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.displayTitle(), style = MaterialTheme.typography.titleMedium)
                    Text(record.displayType(categoryName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${record.displayTime()} · ${record.finalCalories?.let { "$it kcal" } ?: "未记录热量"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onRepeat(record.id) }) { Text("再记一次") }
                        TextButton(onClick = { onOpen(record.id) }) { Text("查看") }
                    }
                }
            }
        }
    }
}

private fun MealRecord.displayTime(): String = Instant.ofEpochMilli(occurredAt)
    .atZone(HabitTimePolicy.zoneId)
    .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun java.time.DayOfWeek.displayName() = listOf("一", "二", "三", "四", "五", "六", "日")[value - 1]
private val DATE_HEADER_FORMAT = DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)
