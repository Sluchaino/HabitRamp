package com.habitramp.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnumsTest {

    @Test
    fun `completed and partial records count as executions`() {
        assertTrue(CompletionStatus.COMPLETED.isExecution)
        assertTrue(CompletionStatus.PARTIAL.isExecution)
    }

    @Test
    fun `missed record does not lock the unit`() {
        assertFalse(CompletionStatus.MISSED.isExecution)
    }

    @Test
    fun `unknown status is treated as missed`() {
        assertFalse(CompletionStatus.fromCode("UNKNOWN").isExecution)
        assertFalse(CompletionStatus.fromCode(null).isExecution)
    }
}
