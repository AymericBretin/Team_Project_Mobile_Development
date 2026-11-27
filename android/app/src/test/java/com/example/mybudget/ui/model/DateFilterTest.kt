package com.example.mybudget.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateFilterTest {
    private val today = LocalDate.of(2026, 3, 15)
    private fun on(date: String) = Expense(0, date, 1.0, date = LocalDate.parse(date))
    private val expenses = listOf("2026-01-31", "2026-02-01", "2026-02-28", "2026-03-01", "2026-03-15").map(::on)

    private fun titles(filter: DateFilter) = expenses.inPeriod(filter, today).map { it.title }

    @Test
    fun `this month and last month are whole calendar months`() {
        assertEquals(listOf("2026-03-01", "2026-03-15"), titles(DateFilter.ThisMonth))
        assertEquals(listOf("2026-02-01", "2026-02-28"), titles(DateFilter.LastMonth))
        assertEquals(expenses.size, titles(DateFilter.AllTime).size)
    }

    @Test
    fun `a custom range includes both ends`() {
        val range = DateFilter.Custom(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1))
        assertEquals(listOf("2026-01-31", "2026-02-01"), titles(range))
    }

    @Test
    fun `survives being saved and restored`() {
        listOf(DateFilter.AllTime, DateFilter.ThisMonth, DateFilter.LastMonth, DateFilter.Custom(today.minusDays(3), today))
            .forEach { assertEquals(it, DateFilter.decode(DateFilter.encode(it))) }
    }
}
