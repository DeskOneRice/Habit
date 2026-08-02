package com.habit.app.ui.diet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit.app.domain.model.RankedValue
import com.habit.app.ui.components.HabitCard
import com.habit.app.ui.components.HabitTopAppBar
import com.habit.app.ui.components.NavigationMode

@Composable
fun DietStatsScreen(viewModel: DietStatsViewModel, onOpenDrawer: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    Scaffold(topBar = { HabitTopAppBar("饮食统计", NavigationMode.MENU, onOpenDrawer) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HabitCard(Modifier.fillMaxWidth()) {
                    Text("最近 7 天", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(summary.totalCalories?.let { "$it kcal" } ?: "暂无热量记录", style = MaterialTheme.typography.headlineMedium)
                    Text("记录 ${summary.recordedDays} 天 · ${summary.recordCount} 条 · 饮品 ${summary.beverageCups} 杯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { RankingCard("常点品牌", summary.brandRanking) }
            item { RankingCard("饮品分类", summary.categoryRanking) }
            item { RankingCard("常选甜度", summary.sweetnessRanking) }
            item { RankingCard("常选冰量", summary.iceRanking) }
        }
    }
}

@Composable
private fun RankingCard(title: String, values: List<RankedValue>) {
    HabitCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (values.isEmpty()) Text("记录更多后会显示统计", color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.take(5).forEachIndexed { index, value ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${index + 1}. ${value.label}")
                Text("${value.count}")
            }
        }
    }
}
