package com.habitramp.domain.calculator

import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.util.DatePeriod
import com.habitramp.core.util.roundToUnit
import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.HabitPause
import com.habitramp.domain.model.ManualResetEvent
import com.habitramp.domain.model.SettingsSnapshot
import java.math.BigDecimal
import java.time.LocalDate

data class DayStatistics(
    val date: LocalDate,
    val actual: BigDecimal,
    val potential: BigDecimal,
    val planned: Boolean,
    val status: CompletionStatus?
)

data class PeriodStatistics(
    val period: DatePeriod,
    val previousPeriod: DatePeriod,
    val actual: BigDecimal,
    val potential: BigDecimal,
    val difference: BigDecimal,
    val previousActual: BigDecimal,
    val previousPotential: BigDecimal,
    val changeAbsolute: BigDecimal,
    val changePercent: Double?,
    val completedDays: Int,
    val partialDays: Int,
    val missedDays: Int,
    val pauseCount: Int,
    val chart: List<DayStatistics>
)

object StatisticsCalculator {

    fun calculate(
        habit: Habit,
        records: List<DailyRecord>,
        pauses: List<HabitPause>,
        settings: List<SettingsSnapshot>,
        manualResets: List<ManualResetEvent>,
        period: DatePeriod,
        previousPeriod: DatePeriod
    ): PeriodStatistics {
        val stopDate = when {
            habit.completedAt != null -> habit.completedAt
            habit.archivedAt != null -> habit.archivedAt.minusDays(1)
            else -> period.to
        }
        val effectivePeriod = DatePeriod(
            period.from,
            if (stopDate.isBefore(period.from)) period.from.minusDays(1) else minOf(period.to, stopDate)
        )
        val previousEffectivePeriod = DatePeriod(
            previousPeriod.from,
            if (stopDate.isBefore(previousPeriod.from)) previousPeriod.from.minusDays(1)
            else minOf(previousPeriod.to, stopDate)
        )
        val simulationEnd = maxOf(period.to, previousPeriod.to)
        val potentialMap = potentialGoals(habit, settings, manualResets, simulationEnd)

        val periodRecords = records.filter { it.date in period }
        val previousRecords = records.filter { it.date in previousPeriod }

        val actual = periodRecords.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.actualValue) }
        val previousActual = previousRecords.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.actualValue) }
        val potential = sumMap(potentialMap, effectivePeriod)
        val previousPotential = sumMap(potentialMap, previousEffectivePeriod)

        val changeAbsolute = actual.subtract(previousActual)
        val changePercent = if (previousActual.compareTo(BigDecimal.ZERO) == 0) {
            null
        } else {
            changeAbsolute
                .divide(previousActual, com.habitramp.core.util.DECIMAL_CONTEXT)
                .multiply(BigDecimal(100))
                .toDouble()
        }

        val days = buildList {
            var date = period.from
            while (!date.isAfter(period.to)) {
                val record = periodRecords.firstOrNull { it.date == date }
                val plannedPotential = if (date.isAfter(stopDate)) null else potentialMap[date]
                add(
                    DayStatistics(
                        date = date,
                        actual = record?.actualValue ?: BigDecimal.ZERO,
                        potential = plannedPotential ?: BigDecimal.ZERO,
                        planned = plannedPotential != null || record != null,
                        status = record?.status
                    )
                )
                date = date.plusDays(1)
            }
        }

        return PeriodStatistics(
            period = period,
            previousPeriod = previousPeriod,
            actual = actual,
            potential = potential,
            difference = potential.subtract(actual),
            previousActual = previousActual,
            previousPotential = previousPotential,
            changeAbsolute = changeAbsolute,
            changePercent = changePercent,
            completedDays = periodRecords.count { it.status == CompletionStatus.COMPLETED },
            partialDays = periodRecords.count { it.status == CompletionStatus.PARTIAL },
            missedDays = periodRecords.count { it.status == CompletionStatus.MISSED },
            pauseCount = pauses.count { it.startDate <= period.to && it.endDate > period.from },
            chart = days
        )
    }

    /**
     * Simulates the "ideal" development sequence: no misses, no pause penalties.
     * Schedule, settings changes, target values and manual resets are respected.
     */
    fun potentialGoals(
        habit: Habit,
        settings: List<SettingsSnapshot>,
        manualResets: List<ManualResetEvent>,
        to: LocalDate
    ): Map<LocalDate, BigDecimal> {
        val result = linkedMapOf<LocalDate, BigDecimal>()
        val start = habit.startDate
        if (to.isBefore(start)) return result

        val snapshots = settings.sortedBy { it.effectiveDate }
        val resets = manualResets.sortedBy { it.effectiveDate }
        var snapshotIndex = 0
        var resetIndex = 0

        var step = habit.step
        var cycleLength = habit.cycleLength
        var weekdays = habit.weekdays
        var target = habit.targetValue
        var behavior = habit.targetBehavior
        var base = habit.initialValue
        var goal = habit.initialValue
        var cycle = 0
        var day = 0
        var targetReached = false

        var date = start
        while (!date.isAfter(to)) {
            while (snapshotIndex < snapshots.size &&
                !snapshots[snapshotIndex].effectiveDate.isAfter(date)
            ) {
                val snapshot = snapshots[snapshotIndex]
                if (snapshot.step.compareTo(step) != 0) {
                    val position = BigDecimal(2 * cycle + day)
                    base = goal.subtract(snapshot.step.multiply(position))
                    step = snapshot.step
                }
                if (snapshot.cycleLength != cycleLength) {
                    base = goal
                    cycle = 0
                    day = 0
                    cycleLength = snapshot.cycleLength
                }
                weekdays = snapshot.weekdays
                if (target?.compareTo(snapshot.targetValue ?: BigDecimal.ZERO) != 0 ||
                    snapshot.targetValue == null
                ) targetReached = false
                target = snapshot.targetValue
                behavior = snapshot.targetBehavior
                snapshotIndex++
            }
            while (resetIndex < resets.size && !resets[resetIndex].effectiveDate.isAfter(date)) {
                base = resets[resetIndex].newGoal
                goal = resets[resetIndex].newGoal
                cycle = 0
                day = 0
                targetReached = false
                resetIndex++
            }

            if (date.dayOfWeek in weekdays) {
                val raw = ProgressionCalculator.rawGoal(base, step, cycle, day)
                    .roundToUnit(habit.unit)
                goal = if (behavior == TargetBehavior.MAINTAIN && targetReached && target != null) {
                    target
                } else ProgressionCalculator.applyTargetCap(raw, target, behavior)
                result[date] = goal
                if (target != null && goal >= target) targetReached = true
                day += 1
                if (day >= cycleLength) {
                    day = 0
                    cycle += 1
                }
                val nextRaw = ProgressionCalculator.rawGoal(base, step, cycle, day)
                    .roundToUnit(habit.unit)
                goal = ProgressionCalculator.applyTargetCap(nextRaw, target, behavior)
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun sumMap(map: Map<LocalDate, BigDecimal>, period: DatePeriod): BigDecimal {
        if (period.days <= 0) return BigDecimal.ZERO
        return map.entries
            .asSequence()
            .filter { it.key in period }
            .fold(BigDecimal.ZERO) { acc, entry -> acc.add(entry.value) }
    }
}
