package com.habitramp.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.util.formatFull
import com.habitramp.core.util.formatValue
import com.habitramp.core.util.formatWithUnit
import com.habitramp.core.util.shortUnitName
import com.habitramp.domain.calculator.UnlockCalculator
import com.habitramp.domain.model.Habit
import com.habitramp.presentation.components.ConfirmDialog
import com.habitramp.presentation.components.HabitSelector
import com.habitramp.presentation.components.InfoRow
import com.habitramp.presentation.components.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: (Long) -> Unit,
    onOpenHabits: () -> Unit,
    onCreateHabit: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedHabit = state.selected
    val snackbarHostState = remember { SnackbarHostState() }
    var resetConfirm by remember { mutableStateOf(false) }
    var actualInput by rememberSaveable(state.selected?.id) { mutableStateOf("") }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(state.error, state.message) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HabitRamp", fontWeight = FontWeight.Bold) },
                actions = {
                    state.selected?.let { habit ->
                        IconButton(onClick = { onOpenSettings(habit.id) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Настройки привычки")
                        }
                    }
                    IconButton(onClick = onOpenHabits) {
                        Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = "Управление привычками")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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

        if (state.habits.isEmpty()) {
            EmptyHabits(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onCreateHabit = onCreateHabit
            )
            return@Scaffold
        }

        val habit = state.selected
        if (habit == null) {
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
                GoalCard(habit = habit, streak = state.streak, plannedToday = state.plannedToday)
            }

            val activePause = state.activePause
            if (activePause != null) {
                item {
                    PauseCard(
                        type = activePause.pauseType,
                        endDate = activePause.endDate,
                        onExtend = viewModel::extendPause
                    )
                }
            } else if (habit.status == HabitStatus.PAUSED) {
                item { PausedCard() }
            } else if (!state.plannedToday) {
                item {
                    RestDayCard(habit = habit)
                }
            } else if (state.todayRecord != null) {
                item {
                    RecordCard(habit = habit, status = state.todayRecord!!.status,
                        planned = state.todayRecord!!.plannedValue,
                        actual = state.todayRecord!!.actualValue)
                }
            } else {
                item {
                    CompletionSection(
                        habit = habit,
                        actualInput = actualInput,
                        onActualInputChange = { actualInput = it },
                        onDone = viewModel::markDone,
                        onNotDone = { viewModel.markNotDone(actualInput) }
                    )
                }
                item {
                    TextButton(onClick = { resetConfirm = true }) {
                        Text("Сбросить нагрузку на 50%")
                    }
                }
            }

            item {
                UnlockCard(
                    points = state.progress.currentPoints,
                    unlockedSlots = state.progress.unlockedSlots,
                    activeCount = state.activeCount
                )
            }

            item {
                CycleCard(habit = habit)
            }
        }
    }

    if (resetConfirm) {
        val newGoal = selectedHabit?.let {
            com.habitramp.domain.calculator.ResetCalculator.newGoal(it.currentGoal, it.unit)
        }
        ConfirmDialog(
            title = "Сбросить нагрузку?",
            text = if (newGoal != null) {
                "Текущая цель ${selectedHabit!!.currentGoal.formatValue(selectedHabit.unit)} будет уменьшена до " +
                    "${newGoal.formatWithUnit(selectedHabit.unit).trimEnd('.')}. " +
                    "Сброс применится сразу, если сегодня запланированный день без отметки, " +
                    "иначе — в следующий запланированный день."
            } else "Текущая цель будет уменьшена на 50%.",
            confirmLabel = "Сбросить",
            onConfirm = {
                resetConfirm = false
                viewModel.manualReset()
            },
            onDismiss = { resetConfirm = false }
        )
    }

    if (state.targetPrompt && selectedHabit != null) {
        val target = selectedHabit.targetValue?.formatWithUnit(selectedHabit.unit)?.trimEnd('.') ?: ""
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Целевое значение достигнуто") },
            text = {
                Text(
                    "Вы достигли цели $target. " +
                        "Что делать дальше?"
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { viewModel.resolveTarget(TargetBehavior.MAINTAIN) }) {
                        Text("Поддерживать значение")
                    }
                    TextButton(onClick = { viewModel.resolveTarget(TargetBehavior.CONTINUE) }) {
                        Text("Продолжать развитие")
                    }
                    TextButton(onClick = { viewModel.resolveTarget(TargetBehavior.FINISH) }) {
                        Text("Завершить привычку")
                    }
                }
            }
        )
    }
}

@Composable
internal fun CompletionSection(
    habit: Habit,
    actualInput: String,
    onActualInputChange: (String) -> Unit,
    onDone: () -> Unit,
    onNotDone: () -> Unit
) {
    val goalText = habit.currentGoal.formatWithUnit(habit.unit)
    SectionCard(title = "Выполнение") {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .size(176.dp)
                    .testTag("home-completion-done"),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Выполнено", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "вся цель",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Text(
            text = "Запишет полную цель: $goalText.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = onNotDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("home-completion-not-done")
        ) {
            Text("Не выполнено")
        }
        Text(
            text = "Запишет фактическое значение из поля ниже. Если оно не меньше цели, " +
                "день будет засчитан как выполненный.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = actualInput,
            onValueChange = onActualInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home-completion-actual"),
            label = { Text("Фактически выполнено") },
            placeholder = { Text("0") },
            suffix = { Text(habit.unit.shortName) },
            supportingText = { Text("Оставьте пустым, чтобы записать 0.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
    }
}

@Composable
private fun GoalCard(habit: Habit, streak: Int, plannedToday: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = habit.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (plannedToday) "Сегодняшняя цель" else "Текущая цель",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = habit.currentGoal.formatValue(habit.unit),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = " ${habit.currentGoal.shortUnitName(habit.unit)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Серия: $streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun RecordCard(
    habit: Habit,
    status: CompletionStatus,
    planned: java.math.BigDecimal,
    actual: java.math.BigDecimal
) {
    val title = when (status) {
        CompletionStatus.COMPLETED -> "Сегодня выполнено"
        CompletionStatus.PARTIAL -> "Частично выполнено"
        CompletionStatus.MISSED -> "Сегодня пропущено"
    }
    val color = when (status) {
        CompletionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        CompletionStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
        CompletionStatus.MISSED -> MaterialTheme.colorScheme.error
    }
    SectionCard(title = title) {
        InfoRow("Цель", planned.formatWithUnit(habit.unit))
        InfoRow("Фактически", actual.formatWithUnit(habit.unit))
        Text(
            text = when (status) {
                CompletionStatus.COMPLETED -> "День засчитан. Новая цель уже рассчитана."
                CompletionStatus.PARTIAL ->
                    "День не засчитан как выполненный. Применено правило обработки пропуска."
                CompletionStatus.MISSED ->
                    "День считается пропущенным. Применено правило обработки пропуска."
            },
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun PauseCard(type: PauseType, endDate: LocalDate, onExtend: () -> Unit) {
    val title = if (type == PauseType.WEEKLY) "Недельная пауза" else "Месячная пауза"
    val reduction = if (type == PauseType.WEEKLY) "40% за каждую неделю" else "75% за месяц"
    SectionCard(title = title) {
        Text(
            text = "Выполнение не требуется. Прогресс снижается по правилам паузы.",
            style = MaterialTheme.typography.bodyMedium
        )
        InfoRow("Окончание", endDate.formatFull())
        InfoRow("Снижение прогресса", reduction)
        OutlinedButton(onClick = onExtend, modifier = Modifier.fillMaxWidth()) {
            Text(if (type == PauseType.WEEKLY) "Продлить на неделю" else "Продлить на месяц")
        }
    }
}

@Composable
private fun PausedCard() {
    SectionCard(title = "Привычка на паузе") {
        Text(
            text = "Выполнение не требуется. Откройте настройки привычки, чтобы продлить паузу " +
                "или изменить параметры.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RestDayCard(habit: Habit) {
    var next = LocalDate.now().plusDays(1)
    var guard = 0
    while (!habit.isScheduledOn(next) && guard < 400) {
        next = next.plusDays(1)
        guard++
    }
    SectionCard(title = "Сегодня выполнение не запланировано") {
        Text(
            text = "Отдыхайте. Следующее запланированное выполнение: ${next.formatFull()}.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun UnlockCard(points: Int, unlockedSlots: Int, activeCount: Int) {
    val maxed = unlockedSlots >= UnlockCalculator.MAX_SLOTS
    val needed = UnlockCalculator.pointsNeededForNextSlot(unlockedSlots)
    SectionCard(title = "Разблокировка привычек") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Баллы: $points", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Привычки: $activeCount / $unlockedSlots",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (maxed) {
            Text("Все ${UnlockCalculator.MAX_SLOTS} слотов открыты.", style = MaterialTheme.typography.bodySmall)
        } else {
            LinearProgressIndicator(
                progress = { (points.toFloat() / needed.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "До нового слота: ${(needed - points).coerceAtLeast(0)} б. " +
                    "Не пропускайте запланированные дни, чтобы не обнулить счётчик.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CycleCard(habit: Habit) {
    SectionCard(title = "Прогресс цикла") {
        InfoRow("Позиция", "${habit.cycleDayNumber} из ${habit.cycleLength}")
        LinearProgressIndicator(
            progress = { habit.cycleDayNumber.toFloat() / habit.cycleLength.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        InfoRow("Ежедневный шаг", "+${habit.step.formatWithUnit(habit.unit)}")
        InfoRow("Единица", habit.unit.displayName)
        if (habit.targetValue != null) {
            InfoRow("Целевое значение", habit.targetValue.formatWithUnit(habit.unit))
        }
    }
}

@Composable
private fun EmptyHabits(modifier: Modifier, onCreateHabit: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Пока нет привычек", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Создайте первую привычку и начните постепенно увеличивать нагрузку.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreateHabit) { Text("Создать привычку") }
    }
}
