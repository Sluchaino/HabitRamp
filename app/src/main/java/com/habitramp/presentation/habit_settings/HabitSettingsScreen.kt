package com.habitramp.presentation.habit_settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.core.util.formatFull
import com.habitramp.core.util.formatValue
import com.habitramp.core.util.formatWithUnit
import com.habitramp.core.util.russianShort
import com.habitramp.domain.model.Habit
import com.habitramp.presentation.components.ConfirmDialog
import com.habitramp.presentation.components.EditValueDialog
import com.habitramp.presentation.components.InfoRow
import com.habitramp.presentation.components.SectionCard
import java.time.DayOfWeek
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSettingsScreen(
    viewModel: HabitSettingsViewModel,
    onBack: () -> Unit,
    onOpenStatistics: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val habit = state.habit
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editName by remember { mutableStateOf(false) }
    var editStep by remember { mutableStateOf(false) }
    var editCycle by remember { mutableStateOf(false) }
    var editReset by remember { mutableStateOf(false) }
    var editPenalty by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf(false) }
    var unitDialog by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    var pauseConfirm by remember { mutableStateOf(false) }
    var archiveConfirm by remember { mutableStateOf(false) }
    var completeConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    LaunchedEffect(state.error, state.message) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessages()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && habit != null) {
            viewModel.setNotifications(true, habit.notificationHour, habit.notificationMinute)
        } else if (!granted) {
            scope.launch {
                snackbar.showSnackbar("Без разрешения на уведомления напоминания не придут")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки привычки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (habit == null) return@Scaffold
        val isLive = habit.status == HabitStatus.ACTIVE || habit.status == HabitStatus.PAUSED

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard(title = habit.name) {
                    Text(
                        text = statusLabel(habit.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    InfoRow("Текущая цель", habit.currentGoal.formatWithUnit(habit.unit))
                    InfoRow("Цикл", "день ${habit.cycleDayNumber} из ${habit.cycleLength}")
                    InfoRow("Создана", habit.createdAt.formatFull())
                    habit.archivedAt?.let { InfoRow("Архивирована", it.formatFull()) }
                    habit.completedAt?.let { InfoRow("Завершена", it.formatFull()) }
                    if (habit.pendingResetDate != null && habit.pendingResetValue != null) {
                        InfoRow(
                            "Отложенный сброс",
                            "${habit.pendingResetValue.formatValue(habit.unit)} с ${habit.pendingResetDate.formatFull()}"
                        )
                    }
                }
            }

            if (isLive) {
                item {
                    SectionCard(title = "Основное") {
                        SettingRow("Название", habit.name) { editName = true }
                        SettingRow(
                            label = "Единица измерения",
                            value = habit.unit.displayName,
                            enabled = !state.hasExecutions,
                            hint = if (state.hasExecutions) "Нельзя изменить после первого выполнения" else null
                        ) {
                            editName = false
                            // Unit selection is handled by a dedicated dialog below.
                            unitDialog = true
                        }
                    }
                }
                item {
                    SectionCard(title = "Прогрессия") {
                        SettingRow("Ежедневный шаг", "+${habit.step.formatWithUnit(habit.unit)}") {
                            editStep = true
                        }
                        SettingRow("Длительность цикла", "${habit.cycleLength} выполнений") {
                            editCycle = true
                        }
                        SettingRow(
                            "Целевое значение",
                            habit.targetValue?.let { it.formatWithUnit(habit.unit) }
                                ?: "Не задано"
                        ) { editTarget = true }
                        if (habit.targetValue != null) {
                            InfoRow("Поведение при достижении", targetBehaviorLabel(habit.targetBehavior))
                        }
                        SettingRow(
                            "Значение полного сброса",
                            habit.resetValue.formatWithUnit(habit.unit)
                        ) { editReset = true }
                    }
                }
                item {
                    SectionCard(title = "Обработка пропусков") {
                        MissPolicy.entries.forEach { policy ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setMissPolicy(policy) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = habit.missPolicy == policy,
                                    onClick = { viewModel.setMissPolicy(policy) }
                                )
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text(policyTitle(policy), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        policyDescription(policy),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (habit.missPolicy == MissPolicy.REDUCE) {
                            HorizontalDivider()
                            SettingRow(
                                "Уменьшение цели",
                                "−${habit.missPenalty.formatWithUnit(habit.unit)}"
                            ) { editPenalty = true }
                        }
                    }
                }
                item {
                    SectionCard(title = "Расписание") {
                        Text(
                            "Цикл учитывает только запланированные выполнения.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(DayOfWeek.entries) { day ->
                                FilterChip(
                                    selected = day in habit.weekdays,
                                    onClick = { viewModel.toggleWeekday(day) },
                                    label = { Text(day.russianShort()) }
                                )
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "Уведомления") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Напоминания", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = habit.notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.setNotifications(
                                            enabled,
                                            habit.notificationHour,
                                            habit.notificationMinute
                                        )
                                    }
                                }
                            )
                        }
                        SettingRow(
                            "Время напоминания",
                            "%02d:%02d".format(habit.notificationHour, habit.notificationMinute)
                        ) {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    viewModel.setNotifications(
                                        habit.notificationsEnabled,
                                        hour,
                                        minute
                                    )
                                },
                                habit.notificationHour,
                                habit.notificationMinute,
                                true
                            ).show()
                        }
                        Text(
                            "Уведомления не приходят во время паузы и в незапланированные дни.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    SectionCard(title = "Нагрузка") {
                        Text(
                            "Ручной сброс уменьшает текущую цель на 50% и начинает новый цикл.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val resetPreview = state.resetPreview
                        if (resetPreview != null) {
                            InfoRow(
                                "Новая цель после сброса",
                                resetPreview.formatWithUnit(habit.unit)
                            )
                        }
                        OutlinedButton(
                            onClick = { resetConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = habit.status == HabitStatus.ACTIVE
                        ) { Text("Сбросить нагрузку") }
                    }
                }
                item {
                    SectionCard(title = "Пауза") {
                        val pause = state.activePause
                        if (pause != null) {
                            InfoRow(
                                "Тип",
                                if (pause.pauseType == PauseType.WEEKLY) "Недельная (−40% за неделю)"
                                else "Месячная (−75% за месяц)"
                            )
                            InfoRow("Окончание", pause.endDate.formatFull())
                            InfoRow("Учтено периодов", pause.processedPeriods.toString())
                            OutlinedButton(
                                onClick = viewModel::extendPause,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (pause.pauseType == PauseType.WEEKLY) "Продлить на неделю" else "Продлить на месяц")
                            }
                        } else {
                            Text(
                                "Во время паузы выполнение не требуется, прогресс постепенно снижается, " +
                                    "баллы не начисляются.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { pauseConfirm = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Начать паузу") }
                        }
                    }
                }
                item {
                    SectionCard(title = "Управление") {
                        OutlinedButton(
                            onClick = { archiveConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Архивировать привычку") }
                        OutlinedButton(
                            onClick = { completeConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Завершить привычку") }
                    }
                }
            }

            item {
                SectionCard(title = "Статистика и история") {
                    OutlinedButton(
                        onClick = { onOpenStatistics(habit.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Статистика") }
                    OutlinedButton(
                        onClick = { onOpenHistory(habit.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("История выполнений") }
                }
            }

            item {
                TextButton(
                    onClick = { deleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Удалить привычку", color = MaterialTheme.colorScheme.error)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ------------------------------------------------------------------ dialogs

    if (habit != null && editName) {
        EditValueDialog(
            title = "Название привычки",
            initial = habit.name,
            label = "Название",
            onConfirm = { editName = false; viewModel.updateName(it) },
            onDismiss = { editName = false }
        )
    }
    if (habit != null && unitDialog) {
        UnitDialog(
            current = habit.unit,
            onSelect = { unitDialog = false; viewModel.updateUnit(it.code) },
            onDismiss = { unitDialog = false }
        )
    }
    if (habit != null && editStep) {
        EditValueDialog(
            title = "Ежедневный шаг",
            initial = habit.step.formatValue(habit.unit),
            label = "Шаг",
            suffix = habit.unit.shortName,
            onConfirm = { editStep = false; viewModel.updateStep(it) },
            onDismiss = { editStep = false }
        )
    }
    if (habit != null && editCycle) {
        EditValueDialog(
            title = "Длительность цикла",
            initial = habit.cycleLength.toString(),
            label = "Количество выполнений",
            onConfirm = { editCycle = false; viewModel.updateCycleLength(it) },
            onDismiss = { editCycle = false }
        )
    }
    if (habit != null && editReset) {
        EditValueDialog(
            title = "Значение полного сброса",
            initial = habit.resetValue.formatValue(habit.unit),
            label = "Значение",
            suffix = habit.unit.shortName,
            onConfirm = { editReset = false; viewModel.updateResetValue(it) },
            onDismiss = { editReset = false }
        )
    }
    if (habit != null && editPenalty) {
        EditValueDialog(
            title = "Уменьшение цели за пропуск",
            initial = habit.missPenalty.formatValue(habit.unit),
            label = "Штраф",
            suffix = habit.unit.shortName,
            onConfirm = { editPenalty = false; viewModel.updatePenalty(it) },
            onDismiss = { editPenalty = false }
        )
    }
    if (habit != null && editTarget) {
        EditValueDialog(
            title = "Целевое значение",
            initial = habit.targetValue?.formatValue(habit.unit) ?: "",
            label = "Оставьте пустым, чтобы убрать ограничение",
            suffix = habit.unit.shortName,
            allowEmpty = true,
            onConfirm = { editTarget = false; viewModel.updateTarget(it) },
            onDismiss = { editTarget = false }
        )
    }
    if (resetConfirm) {
        ConfirmDialog(
            title = "Сбросить нагрузку?",
            text = "Текущая цель уменьшится на 50%, начнётся новый цикл. История сохранится.",
            confirmLabel = "Сбросить",
            onConfirm = { resetConfirm = false; viewModel.manualReset() },
            onDismiss = { resetConfirm = false }
        )
    }
    if (pauseConfirm) {
        AlertDialog(
            onDismissRequest = { pauseConfirm = false },
            title = { Text("Выберите тип паузы") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Недельная: 7 дней, прогресс уменьшается на 40% за каждую неделю.")
                    Text("Месячная: 1 календарный месяц, прогресс уменьшается на 75%.")
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        pauseConfirm = false
                        viewModel.startPause(PauseType.WEEKLY)
                    }) { Text("Недельная пауза") }
                    TextButton(onClick = {
                        pauseConfirm = false
                        viewModel.startPause(PauseType.MONTHLY)
                    }) { Text("Месячная пауза") }
                }
            },
            dismissButton = { TextButton(onClick = { pauseConfirm = false }) { Text("Отмена") } }
        )
    }
    if (archiveConfirm) {
        ConfirmDialog(
            title = "Архивировать привычку?",
            text = "Привычка исчезнет из активных, история и статистика сохранятся. Слот освободится. " +
                "Восстановление не предусмотрено.",
            confirmLabel = "Архивировать",
            onConfirm = { archiveConfirm = false; viewModel.archive() },
            onDismiss = { archiveConfirm = false }
        )
    }
    if (completeConfirm) {
        ConfirmDialog(
            title = "Завершить привычку?",
            text = "Выполнение больше не потребуется. История и статистика сохранятся, слот освободится.",
            confirmLabel = "Завершить",
            onConfirm = { completeConfirm = false; viewModel.complete() },
            onDismiss = { completeConfirm = false }
        )
    }
    if (deleteConfirm) {
        ConfirmDialog(
            title = "Удалить привычку?",
            text = "Будут удалены привычка, история выполнений, события, паузы и история настроек. " +
                "Действие необратимо.",
            confirmLabel = "Удалить",
            destructive = true,
            onConfirm = { deleteConfirm = false; viewModel.delete() },
            onDismiss = { deleteConfirm = false }
        )
    }
    if (state.targetPrompt && habit != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Целевое значение достигнуто") },
            text = { Text("Что делать с привычкой дальше?") },
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
private fun SettingRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    hint: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (enabled) {
            Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun UnitDialog(
    current: MeasurementUnit,
    onSelect: (MeasurementUnit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Единица измерения") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(Units.ALL) { unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(unit) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = unit.code == current.code, onClick = { onSelect(unit) })
                        Column {
                            Text(unit.displayName)
                            Text(
                                unit.category.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private fun statusLabel(status: HabitStatus): String = when (status) {
    HabitStatus.ACTIVE -> "Активна"
    HabitStatus.PAUSED -> "На паузе"
    HabitStatus.ARCHIVED -> "В архиве"
    HabitStatus.COMPLETED -> "Завершена"
}

private fun targetBehaviorLabel(behavior: TargetBehavior): String = when (behavior) {
    TargetBehavior.ASK -> "Ожидает решения"
    TargetBehavior.MAINTAIN -> "Поддерживать значение"
    TargetBehavior.CONTINUE -> "Продолжать развитие"
    TargetBehavior.FINISH -> "Завершить"
}

private fun policyTitle(policy: MissPolicy): String = when (policy) {
    MissPolicy.RESTART -> "Начать заново"
    MissPolicy.REDUCE -> "Уменьшить текущее значение"
    MissPolicy.KEEP -> "Оставить без изменений"
}

private fun policyDescription(policy: MissPolicy): String = when (policy) {
    MissPolicy.RESTART -> "Прогресс вернётся к значению полного сброса"
    MissPolicy.REDUCE -> "Текущая цель уменьшится на заданный штраф"
    MissPolicy.KEEP -> "Прогрессия продолжится по обычному алгоритму"
}
