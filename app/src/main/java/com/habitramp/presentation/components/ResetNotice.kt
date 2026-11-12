package com.habitramp.presentation.components

import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.util.formatFull
import com.habitramp.core.util.formatValue
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Message shown after a manual reset request. A reset is either applied
 * immediately or scheduled for the next planned day. In the deferred case the UI
 * must not claim that the new goal is already in effect.
 */
fun manualResetMessage(
    newGoal: BigDecimal?,
    pendingDate: LocalDate?,
    unit: MeasurementUnit
): String = when {
    newGoal == null -> "Сброс недоступен"
    pendingDate != null -> "Сброс запланирован на ${pendingDate.formatFull()}"
    else -> "Новая цель: ${newGoal.formatValue(unit)}"
}
