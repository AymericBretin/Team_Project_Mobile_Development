package com.example.mybudget.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountTest {
    @Test
    fun `parses dot and comma decimals`() {
        assertEquals(12.5, "12.5".toAmountOrNull()!!, 1e-9)
        assertEquals(12.5, "12,5".toAmountOrNull()!!, 1e-9)
    }

    @Test
    fun `rejects empty, zero and invalid amounts`() {
        listOf("", ".", "0", "0.00", "abc").forEach { assertNull(it, it.toAmountOrNull()) }
    }

    @Test
    fun `formats amounts as plain editable text`() {
        assertEquals("10", 10.0.toAmountText())
        assertEquals("13.99", 13.99.toAmountText())
        assertEquals("12.5", 12.5.toAmountText())
        assertEquals("10000000", 1.0E7.toAmountText())
    }

    @Test
    fun `formatted text parses back to the same amount`() {
        listOf(10.0, 13.99, 9000.0, 12345678.5).forEach {
            assertEquals(it, it.toAmountText().toAmountOrNull()!!, 1e-9)
        }
    }
}
