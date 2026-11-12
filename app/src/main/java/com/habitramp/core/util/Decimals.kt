package com.habitramp.core.util

import com.habitramp.core.model.MeasurementUnit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

val DECIMAL_CONTEXT: MathContext = MathContext(24, RoundingMode.HALF_UP)

/** Removes trailing zeroes without switching to scientific notation. */
fun BigDecimal.clean(): BigDecimal {
    val stripped = stripTrailingZeros()
    return if (stripped.scale() < 0) stripped.setScale(0) else stripped
}

/** Exact textual representation used in the database. */
fun BigDecimal.toStorage(): String = clean().toPlainString()

fun String?.toDecimalOrZero(): BigDecimal =
    this?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull() ?: BigDecimal.ZERO

fun String?.toDecimalOrNull(): BigDecimal? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.replace(',', '.')?.toBigDecimalOrNull()

/** Parses a value entered by the user (accepts both comma and dot). */
fun parseDecimalInput(text: String): BigDecimal? =
    text.trim().replace(" ", "").replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

fun BigDecimal.roundToUnit(unit: MeasurementUnit, mode: RoundingMode = RoundingMode.HALF_UP): BigDecimal {
    var result = setScale(unit.decimals, mode)
    if (result < unit.minGoal) result = unit.minGoal
    return result.clean()
}

/**
 * Rounds a measured value (what the user actually did) to the unit precision.
 * Unlike goals, an actual value may legitimately be zero or below the minimum goal.
 */
fun BigDecimal.roundActualToUnit(
    unit: MeasurementUnit,
    mode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal {
    val result = setScale(unit.decimals, mode).clean()
    return if (result < BigDecimal.ZERO) BigDecimal.ZERO else result
}

/** Human-friendly value, e.g. "12,5" or "30". */
fun BigDecimal.formatValue(unit: MeasurementUnit): String {
    val scaled = setScale(unit.decimals, RoundingMode.HALF_UP).stripTrailingZeros()
    val plain = if (scaled.scale() < 0) scaled.setScale(0).toPlainString() else scaled.toPlainString()
    return plain.replace('.', ',')
}

fun BigDecimal.formatValue(): String {
    val scaled = stripTrailingZeros()
    val plain = if (scaled.scale() < 0) scaled.setScale(0).toPlainString() else scaled.toPlainString()
    return plain.replace('.', ',')
}

/** Short unit label that agrees with the value: "1 раз", "3 раза", "5 раз". */
fun BigDecimal.shortUnitName(unit: MeasurementUnit): String {
    val forms = unit.pluralShortNames ?: return unit.shortName
    val n = abs().setScale(0, RoundingMode.DOWN).toLong()
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> forms[2]
        mod10 == 1L -> forms[0]
        mod10 in 2..4 -> forms[1]
        else -> forms[2]
    }
}

/** Value with a grammatically agreeing unit label, e.g. "3 раза". */
fun BigDecimal.formatWithUnit(unit: MeasurementUnit): String =
    "${formatValue(unit)} ${shortUnitName(unit)}"

fun BigDecimal.coerceAtLeastMin(unit: MeasurementUnit): BigDecimal =
    if (this < unit.minGoal) unit.minGoal else this

fun BigDecimal.percentOf(other: BigDecimal): Double {
    if (other.compareTo(BigDecimal.ZERO) == 0) return 0.0
    return this.divide(other, DECIMAL_CONTEXT).multiply(BigDecimal(100)).toDouble()
}
