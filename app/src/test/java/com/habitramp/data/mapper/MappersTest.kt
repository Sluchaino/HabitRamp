package com.habitramp.data.mapper

import com.habitramp.assertDecimal
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.habit
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `weekdays round trip through the database string`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(days, decodeWeekdays(encodeWeekdays(days)))
    }

    @Test
    fun `empty or invalid weekday strings fall back to every day`() {
        assertEquals(DayOfWeek.entries.toSet(), decodeWeekdays(""))
        assertEquals(DayOfWeek.entries.toSet(), decodeWeekdays("0,8,abc"))
    }

    @Test
    fun `habit entity round trip keeps exact decimals and dates`() {
        val original = habit(
            id = 7,
            name = "Чтение",
            initialValue = "1.25",
            resetValue = "0.50",
            progressionBaseValue = "1.25",
            currentGoal = "3.75",
            unit = Units.ALL.first { it.code == "pages" },
            step = "0.25",
            cycleLength = 5,
            cycleIndex = 2,
            dayIndex = 3,
            targetValue = "20.50",
            targetBehavior = TargetBehavior.MAINTAIN,
            weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            missPolicy = MissPolicy.REDUCE,
            missPenalty = "0.25",
            status = HabitStatus.PAUSED,
            startDate = LocalDate.of(2026, 2, 10),
            createdAt = LocalDate.of(2026, 2, 9),
            lastProcessedDate = LocalDate.of(2026, 3, 1),
            pendingResetValue = "2.25",
            pendingResetDate = LocalDate.of(2026, 3, 5)
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertDecimal("1.25", restored.initialValue)
        assertDecimal("0.5", restored.resetValue)
        assertDecimal("3.75", restored.currentGoal)
        assertDecimal("0.25", restored.step)
        assertEquals(original.unit, restored.unit)
        assertEquals(5, restored.cycleLength)
        assertEquals(2, restored.cycleIndex)
        assertEquals(3, restored.dayIndex)
        assertDecimal("20.5", restored.targetValue!!)
        assertEquals(TargetBehavior.MAINTAIN, restored.targetBehavior)
        assertEquals(original.weekdays, restored.weekdays)
        assertEquals(MissPolicy.REDUCE, restored.missPolicy)
        assertDecimal("0.25", restored.missPenalty)
        assertEquals(HabitStatus.PAUSED, restored.status)
        assertEquals(original.startDate, restored.startDate)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.lastProcessedDate, restored.lastProcessedDate)
        assertDecimal("2.25", restored.pendingResetValue!!)
        assertEquals(original.pendingResetDate, restored.pendingResetDate)
        assertNull(restored.archivedAt)
        assertNull(restored.completedAt)
    }
}
