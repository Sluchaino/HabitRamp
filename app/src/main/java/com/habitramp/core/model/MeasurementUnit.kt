package com.habitramp.core.model

import java.math.BigDecimal

enum class UnitCategory(val title: String) {
    COUNT("Количество"),
    TIME("Время"),
    DISTANCE("Расстояние"),
    MASS("Масса"),
    VOLUME("Объём")
}

/**
 * A unit of measurement. Values are stored as exact decimals; [decimals] defines
 * the precision that is meaningful to the user.
 */
data class MeasurementUnit(
    val code: String,
    val displayName: String,
    val shortName: String,
    val category: UnitCategory,
    val decimals: Int,
    /**
     * Russian plural forms (one, few, many) for short names that are full words,
     * e.g. "1 раз", "3 раза", "5 раз". Abbreviations such as "повт." leave it null.
     */
    val pluralShortNames: List<String>? = null
) {
    val isInteger: Boolean get() = decimals == 0

    val minGoal: BigDecimal
        get() = if (isInteger) BigDecimal.ONE else BigDecimal("0.01")
}

object Units {
    val ALL: List<MeasurementUnit> = listOf(
        MeasurementUnit("reps", "Повторения", "повт.", UnitCategory.COUNT, 0),
        MeasurementUnit(
            "times", "Разы", "раз", UnitCategory.COUNT, 0,
            pluralShortNames = listOf("раз", "раза", "раз")
        ),
        MeasurementUnit("steps", "Шаги", "шаг.", UnitCategory.COUNT, 0),
        MeasurementUnit("pages", "Страницы", "стр.", UnitCategory.COUNT, 0),
        MeasurementUnit("seconds", "Секунды", "сек.", UnitCategory.TIME, 2),
        MeasurementUnit("minutes", "Минуты", "мин.", UnitCategory.TIME, 2),
        MeasurementUnit("hours", "Часы", "ч.", UnitCategory.TIME, 2),
        MeasurementUnit("mm", "Миллиметры", "мм", UnitCategory.DISTANCE, 2),
        MeasurementUnit("cm", "Сантиметры", "см", UnitCategory.DISTANCE, 2),
        MeasurementUnit("m", "Метры", "м", UnitCategory.DISTANCE, 2),
        MeasurementUnit("km", "Километры", "км", UnitCategory.DISTANCE, 2),
        MeasurementUnit("mg", "Миллиграммы", "мг", UnitCategory.MASS, 2),
        MeasurementUnit("g", "Граммы", "г", UnitCategory.MASS, 2),
        MeasurementUnit("kg", "Килограммы", "кг", UnitCategory.MASS, 2),
        MeasurementUnit("ml", "Миллилитры", "мл", UnitCategory.VOLUME, 2),
        MeasurementUnit("l", "Литры", "л", UnitCategory.VOLUME, 2)
    )

    val DEFAULT: MeasurementUnit = ALL.first { it.code == "reps" }

    fun byCode(code: String?): MeasurementUnit =
        ALL.firstOrNull { it.code == code } ?: DEFAULT

    fun forCategory(category: UnitCategory): List<MeasurementUnit> =
        ALL.filter { it.category == category }
}
