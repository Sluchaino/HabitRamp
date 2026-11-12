package com.habitramp.presentation.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.util.formatWithUnit
import com.habitramp.domain.calculator.UnlockCalculator
import com.habitramp.domain.model.Habit
import com.habitramp.presentation.components.SectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHabitsScreen(
    viewModel: ManageHabitsViewModel,
    onCreateHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Привычки") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.canCreate) {
                        onCreateHabit()
                    } else {
                        scope.launch {
                            snackbar.showSnackbar(
                                "Нет свободного слота. Выполняйте привычки, чтобы накопить баллы."
                            )
                        }
                    }
                }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Создать привычку")
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard(title = "Слоты") {
                    Text(
                        "Занято: ${state.activeCount} из ${state.progress.unlockedSlots}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val maxed = state.progress.unlockedSlots >= UnlockCalculator.MAX_SLOTS
                    if (maxed) {
                        Text(
                            "Все ${UnlockCalculator.MAX_SLOTS} слотов открыты.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val needed = UnlockCalculator.pointsNeededForNextSlot(state.progress.unlockedSlots)
                        LinearProgressIndicator(
                            progress = {
                                (state.progress.currentPoints.toFloat() / needed.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Баллы разблокировки: ${state.progress.currentPoints} / $needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Архивация и завершение освобождают слот. Уже открытые слоты сохраняются.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.active.isEmpty() && state.archived.isEmpty() && state.completed.isEmpty()) {
                item {
                    Text(
                        "Привычек пока нет. Нажмите +, чтобы создать первую.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (state.active.isNotEmpty()) {
                item { SectionTitle("Активные") }
                items(state.active, key = { it.id }) { habit ->
                    HabitRow(habit) { onOpenHabit(habit.id) }
                }
            }

            if (state.archived.isNotEmpty()) {
                item { SectionTitle("Архив") }
                items(state.archived, key = { it.id }) { habit ->
                    HabitRow(habit) { onOpenHabit(habit.id) }
                }
            }

            if (state.completed.isNotEmpty()) {
                item { SectionTitle("Завершённые") }
                items(state.completed, key = { it.id }) { habit ->
                    HabitRow(habit) { onOpenHabit(habit.id) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun HabitRow(habit: Habit, onClick: () -> Unit) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${habit.currentGoal.formatWithUnit(habit.unit)} · " +
                        statusText(habit.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

private fun statusText(status: HabitStatus): String = when (status) {
    HabitStatus.ACTIVE -> "активна"
    HabitStatus.PAUSED -> "на паузе"
    HabitStatus.ARCHIVED -> "в архиве"
    HabitStatus.COMPLETED -> "завершена"
}
