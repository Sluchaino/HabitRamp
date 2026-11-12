package com.habitramp.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.util.formatFull
import com.habitramp.core.util.formatMonthYear
import com.habitramp.core.util.formatWithUnit
import com.habitramp.core.util.russianShort
import com.habitramp.presentation.components.HabitSelector
import com.habitramp.presentation.components.InfoRow
import com.habitramp.presentation.components.SectionCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val habit = state.habit

    Scaffold(
        topBar = { TopAppBar(title = { Text("История") }) }
    ) { padding ->
        if (state.loading && habit == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (habit == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("Нет привычек для отображения истории") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.habits.size > 1) {
                HabitSelector(
                    habits = state.habits,
                    selectedId = habit.id,
                    onSelect = viewModel::selectHabit
                )
            }
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Предыдущий месяц")
                    }
                    Text(
                        state.month.atDay(1).formatMonthYear(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Следующий месяц")
                    }
                }
                Calendar(
                    month = state.month,
                    state = state,
                    onSelectDay = viewModel::selectDay
                )
                Legend()
            }
        }
    }

    val detail = state.detail
    if (detail != null && habit != null) {
        val recordStatus = when (detail.status) {
            CompletionStatus.COMPLETED -> "Выполнено"
            CompletionStatus.PARTIAL -> "Частично выполнено"
            CompletionStatus.MISSED -> "Пропущено"
            null -> when {
                detail.isPause -> "Пауза"
                !habit.isScheduledOn(detail.date) -> "Не запланировано"
                detail.date.isAfter(LocalDate.now()) -> "Будущая дата"
                else -> "Нет записи"
            }
        }
        AlertDialog(
            onDismissRequest = viewModel::clearDetail,
            title = { Text(detail.date.formatFull()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Состояние", recordStatus)
                    detail.plannedValue?.let {
                        InfoRow("Цель", it.formatWithUnit(habit.unit))
                    }
                    detail.actualValue?.let {
                        InfoRow("Фактически", it.formatWithUnit(habit.unit))
                    }
                    if (detail.isPause) {
                        InfoRow("Пауза до", detail.pauseEnd?.formatFull() ?: "—")
                    }
                    if (detail.isManualReset) {
                        InfoRow("Ручной сброс", "нагрузка уменьшена на 50%")
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::clearDetail) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun Calendar(
    month: YearMonth,
    state: HistoryUiState,
    onSelectDay: (LocalDate) -> Unit
) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = ((offset + days + 6) / 7)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.russianShort(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    if (index < offset || index >= offset + days) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                    } else {
                        val date = first.plusDays((index - offset).toLong())
                        DayCell(
                            modifier = Modifier.weight(1f),
                            date = date,
                            state = state,
                            selected = state.detail?.date == date,
                            onClick = { onSelectDay(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    date: LocalDate,
    state: HistoryUiState,
    selected: Boolean,
    onClick: () -> Unit
) {
    val record = state.records.firstOrNull { it.date == date }
    val pause = state.pauses.firstOrNull { it.covers(date) }
    val reset = state.resets.any { it.effectiveDate == date }
    val baseColor = when {
        record?.status == CompletionStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        record?.status == CompletionStatus.PARTIAL -> MaterialTheme.colorScheme.tertiaryContainer
        record?.status == CompletionStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
        pause != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val border = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
    } else Modifier
    Box(
        modifier = modifier
            .padding(2.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(border)
            .background(baseColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal
            )
            if (reset) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LegendItem(MaterialTheme.colorScheme.primaryContainer, "Выполнено")
        LegendItem(MaterialTheme.colorScheme.tertiaryContainer, "Частично")
        LegendItem(MaterialTheme.colorScheme.errorContainer, "Пропуск")
        LegendItem(MaterialTheme.colorScheme.secondaryContainer, "Пауза")
        LegendItem(MaterialTheme.colorScheme.tertiary, "Сброс")
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(" $text", style = MaterialTheme.typography.bodySmall)
    }
}
