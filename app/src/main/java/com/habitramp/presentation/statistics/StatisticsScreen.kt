package com.habitramp.presentation.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitramp.core.util.formatShort
import com.habitramp.core.util.formatValue
import com.habitramp.core.util.formatWithUnit
import com.habitramp.data.repository.StatisticsPeriod
import com.habitramp.domain.calculator.PeriodStatistics
import com.habitramp.domain.model.Habit
import com.habitramp.presentation.components.HabitSelector
import com.habitramp.presentation.components.InfoRow
import com.habitramp.presentation.components.SectionCard
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Статистика") }) }
    ) { padding ->
        if (state.loading && state.habit == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val habit = state.habit
        if (habit == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("Нет привычек для отображения статистики") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.habits.size > 1) {
                item {
                    HabitSelector(
                        habits = state.habits,
                        selectedId = habit.id,
                        onSelect = viewModel::selectHabit
                    )
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    StatisticsPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = state.period == period,
                            onClick = { viewModel.setPeriod(period) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = StatisticsPeriod.entries.size
                            )
                        ) {
                            Text(if (period == StatisticsPeriod.WEEK) "Неделя" else "Месяц")
                        }
                    }
                }
            }

            val stats = state.stats
            if (stats == null) {
                item {
                    if (state.loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        Text(state.error ?: "Статистика недоступна")
                    }
                }
            } else {
                item { SummaryCard(habit, stats) }
                item { ComparisonCard(habit, stats) }
                item { DetailsCard(habit, stats) }
                item { LoadChart(habit, stats) }
            }
        }
    }
}

@Composable
private fun SummaryCard(habit: Habit, stats: PeriodStatistics) {
    SectionCard(title = "Итоги периода") {
        Text(
            "${stats.period.from.formatShort()} — ${stats.period.to.formatShort()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                label = "Фактически",
                value = stats.actual.formatValue(habit.unit),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Потенциально",
                value = stats.potential.formatValue(habit.unit),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                label = "Разница",
                value = stats.difference.formatValue(habit.unit),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Пропущено дней",
                value = stats.missedDays.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        InfoRow("Полностью выполнено", stats.completedDays.toString())
        InfoRow("Частично выполнено", stats.partialDays.toString())
        InfoRow("Использовано пауз", stats.pauseCount.toString())
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComparisonCard(habit: Habit, stats: PeriodStatistics) {
    val changePercent = stats.changePercent
    val percentText = if (changePercent == null) {
        "не рассчитывается (предыдущий период = 0)"
    } else {
        String.format(Locale("ru", "RU"), "%+.1f%%", changePercent)
    }
    SectionCard(title = "Сравнение с предыдущим периодом") {
        Text(
            "Предыдущий период: ${stats.previousPeriod.from.formatShort()} — ${stats.previousPeriod.to.formatShort()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        InfoRow("Фактически тогда", stats.previousActual.formatValue(habit.unit))
        InfoRow("Потенциально тогда", stats.previousPotential.formatValue(habit.unit))
        InfoRow("Абсолютное изменение", stats.changeAbsolute.formatWithUnit(habit.unit))
        InfoRow("Процентное изменение", percentText)
        Text(
            "Текущий период сравнивается с таким же количеством прошедших дней предыдущего периода.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailsCard(habit: Habit, stats: PeriodStatistics) {
    SectionCard(title = "Показатели") {
        InfoRow("Фактический результат", stats.actual.formatWithUnit(habit.unit))
        InfoRow("Потенциальный результат", stats.potential.formatWithUnit(habit.unit))
        InfoRow("Разница", stats.difference.formatWithUnit(habit.unit))
        Text(
            "Потенциальный результат учитывает расписание, настройки, целевые значения и ручные сбросы, " +
                "но не учитывает пропуски и снижение прогресса из-за пауз.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadChart(habit: Habit, stats: PeriodStatistics) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    SectionCard(title = "График нагрузки") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = primary, text = "Фактически")
            LegendDot(color = tertiary, text = "Потенциально")
        }
        val maxValue = stats.chart.maxOfOrNull {
            maxOf(it.actual, it.potential).toFloat()
        } ?: 0f
        if (maxValue <= 0f) {
            Text("Нет данных за период", style = MaterialTheme.typography.bodyMedium)
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val count = stats.chart.size.coerceAtLeast(1)
                val group = size.width / count
                val barWidth = (group * 0.28f).coerceAtLeast(2f)
                stats.chart.forEachIndexed { index, day ->
                    val actualHeight = day.actual.toFloat() / maxValue * size.height
                    val potentialHeight = day.potential.toFloat() / maxValue * size.height
                    drawRect(
                        color = primary,
                        topLeft = Offset(index * group + group * 0.14f, size.height - actualHeight),
                        size = Size(barWidth, actualHeight)
                    )
                    drawRect(
                        color = tertiary.copy(alpha = 0.55f),
                        topLeft = Offset(index * group + group * 0.56f, size.height - potentialHeight),
                        size = Size(barWidth, potentialHeight)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stats.period.from.formatShort(), style = MaterialTheme.typography.bodySmall)
            Text(stats.period.to.formatShort(), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Максимум на графике: ${maxValue.toBigDecimal().formatWithUnit(habit.unit)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.height(10.dp)) {
            drawRect(color = color, size = Size(10.dp.toPx(), 10.dp.toPx()))
        }
        Text("  $text", style = MaterialTheme.typography.bodySmall)
    }
}
