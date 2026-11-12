package com.habitramp.core.util

import com.habitramp.assertDecimal
import com.habitramp.core.model.Units
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalsTest {

    private val reps = Units.DEFAULT
    private val minutes = Units.ALL.first { it.code == "minutes" }

    @Test
    fun `storage representation removes trailing zeroes`() {
        assertEquals("1.5", BigDecimal("1.500").toStorage())
        assertEquals("0.01", BigDecimal("0.0100").toStorage())
        assertEquals("1000", BigDecimal("1E+3").toStorage())
        assertEquals("0", BigDecimal("0.000").toStorage())
    }

    @Test
    fun `user input accepts comma dot and spaces`() {
        assertDecimal("1234.5", parseDecimalInput(" 1 234,50 ")!!)
        assertDecimal("2.75", parseDecimalInput("2.75")!!)
        assertNull(parseDecimalInput(""))
        assertNull(parseDecimalInput("abc"))
    }

    @Test
    fun `stored strings are parsed safely`() {
        assertDecimal("2.5", "2,5".toDecimalOrNull()!!)
        assertNull(null.toDecimalOrNull())
        assertNull("".toDecimalOrNull())
        assertDecimal("0", null.toDecimalOrZero())
        assertDecimal("3.14", "3.14".toDecimalOrZero())
    }

    @Test
    fun `rounding respects unit precision`() {
        assertDecimal("11", BigDecimal("10.5").roundToUnit(reps))
        assertDecimal("1.23", BigDecimal("1.234").roundToUnit(minutes))
        assertDecimal("1.24", BigDecimal("1.235").roundToUnit(minutes))
    }

    @Test
    fun `rounding never goes below the unit minimum`() {
        assertDecimal("1", BigDecimal("0.4").roundToUnit(reps))
        assertDecimal("0.01", BigDecimal("0.001").roundToUnit(minutes))
        assertDecimal("0.01", BigDecimal.ZERO.roundToUnit(minutes))
    }

    @Test
    fun `actual values may be zero and are not raised to the unit minimum`() {
        assertDecimal("0", BigDecimal.ZERO.roundActualToUnit(reps))
        assertDecimal("0", BigDecimal("0.4").roundActualToUnit(reps))
        assertDecimal("3", BigDecimal("3.4").roundActualToUnit(reps))
        assertDecimal("0", BigDecimal.ZERO.roundActualToUnit(minutes))
        assertDecimal("0.01", BigDecimal("0.014").roundActualToUnit(minutes))
        assertDecimal("0", BigDecimal("-2").roundActualToUnit(reps))
    }

    @Test
    fun `formatting uses a comma and drops trailing zeroes`() {
        assertEquals("12,5", BigDecimal("12.50").formatValue(minutes))
        assertEquals("12", BigDecimal("12.00").formatValue(reps))
        assertEquals("1000", BigDecimal("1E+3").formatValue())
    }

    @Test
    fun `unit labels agree with russian plural rules`() {
        val times = Units.ALL.first { it.code == "times" }
        assertEquals("1 раз", BigDecimal("1").formatWithUnit(times))
        assertEquals("3 раза", BigDecimal("3").formatWithUnit(times))
        assertEquals("5 раз", BigDecimal("5").formatWithUnit(times))
        assertEquals("11 раз", BigDecimal("11").formatWithUnit(times))
        assertEquals("21 раз", BigDecimal("21").formatWithUnit(times))
        assertEquals("22 раза", BigDecimal("22").formatWithUnit(times))
        assertEquals("1 повт.", BigDecimal.ONE.formatWithUnit(reps))
        assertEquals("2,5 мин.", BigDecimal("2.5").formatWithUnit(minutes))
    }

    @Test
    fun `percent of zero is safe`() {        assertEquals(50.0, BigDecimal("5").percentOf(BigDecimal("10")), 0.0001)
        assertEquals(0.0, BigDecimal("5").percentOf(BigDecimal.ZERO), 0.0001)
    }

    @Test
    fun `coerce at least minimum uses the unit`() {
        assertDecimal("1", BigDecimal("0.2").coerceAtLeastMin(reps))
        assertDecimal("5", BigDecimal("5").coerceAtLeastMin(reps))
        assertDecimal("0.01", BigDecimal("0.001").coerceAtLeastMin(minutes))
    }

    @Test
    fun `unit minimum matches its precision`() {
        assertDecimal("1", reps.minGoal)
        assertDecimal("0.01", minutes.minGoal)
    }
}
