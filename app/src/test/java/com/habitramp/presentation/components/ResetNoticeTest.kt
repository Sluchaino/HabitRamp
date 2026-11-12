package com.habitramp.presentation.components

import com.habitramp.core.model.Units
import com.habitramp.core.util.formatFull
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResetNoticeTest {

    private val reps = Units.DEFAULT
    private val date = LocalDate.of(2026, 3, 3)

    @Test
    fun `deferred reset reports its planned date instead of a new goal`() {
        assertEquals(
            "Сброс запланирован на ${date.formatFull()}",
            manualResetMessage(BigDecimal("5"), date, reps)
        )
    }

    @Test
    fun `immediate reset reports the new goal`() {
        assertEquals(
            "Новая цель: 5",
            manualResetMessage(BigDecimal("5"), null, reps)
        )
    }

    @Test
    fun `unavailable reset is reported explicitly`() {
        assertEquals(
            "Сброс недоступен",
            manualResetMessage(null, null, reps)
        )
    }

    @Test
    fun `unavailable reset ignores a stale pending date`() {
        assertEquals(
            "Сброс недоступен",
            manualResetMessage(null, date, reps)
        )
    }
}
