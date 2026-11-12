package com.habitramp.presentation.habit_create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitramp.core.model.Units
import com.habitramp.domain.calculator.UnlockCalculator
import com.habitramp.presentation.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHabitScreen(
    viewModel: CreateHabitViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedId) {
        state.savedId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая привычка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard(title = "Основное") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Название привычки") },
                    placeholder = { Text("Например, Отжимания") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.initialValue,
                    onValueChange = viewModel::setInitialValue,
                    label = { Text("Начальное значение") },
                    placeholder = { Text("Например, 1") },
                    suffix = { Text(state.unit.shortName) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = state.unit.displayName,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Единица измерения") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Units.ALL.forEach { unit ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(unit.displayName)
                                        Text(
                                            unit.category.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setUnit(unit)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = if (state.unit.isInteger) {
                        "Для этой единицы используются целые значения."
                    } else {
                        "Для этой единицы допускаются дробные значения (до ${state.unit.decimals} знаков)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "Доступные слоты") {
                Text(
                    "Занято привычек: ${state.activeCount} из ${state.progress.unlockedSlots}",
                    style = MaterialTheme.typography.bodyMedium
                )
                val maxed = state.progress.unlockedSlots >= UnlockCalculator.MAX_SLOTS
                if (!maxed) {
                    val needed = UnlockCalculator.pointsNeededForNextSlot(state.progress.unlockedSlots)
                    LinearProgressIndicator(
                        progress = {
                            (state.progress.currentPoints.toFloat() / needed.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Баллы: ${state.progress.currentPoints} / $needed до следующего слота",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!state.canCreate) {
                    Text(
                        "Свободных слотов нет. Выполняйте запланированные привычки, чтобы накопить баллы, " +
                            "или освободите слот, архивировав привычку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "Привычка начнёт требовать выполнения со следующего подходящего запланированного дня. " +
                    "Остальные параметры можно настроить позже.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = viewModel::save,
                enabled = state.canCreate && !state.saving &&
                    state.name.isNotBlank() && state.initialValue.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (state.saving) "Создание..." else "Создать привычку")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
