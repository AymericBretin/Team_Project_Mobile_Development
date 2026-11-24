package com.example.mybudget.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun expense(amount: Double, currency: String = "EUR", category: String? = null, date: LocalDate = today) =
        Expense(id = 0, title = "x", amount = amount, currency = currency, category = category, date = date)

    @Test
    fun `converts to and from EUR, unknown currencies count as EUR`() {
        assertEquals(9.2, ExchangeRates.toEur(10.0, "USD"), 1e-9)
        assertEquals(10.0, ExchangeRates.fromEur(9.2, "USD"), 1e-9)
        assertEquals(10.0, ExchangeRates.toEur(10.0, "XXX"), 1e-9)
    }

    @Test
    fun `inMonth keeps only the expenses of that month`() {
        val inside = expense(1.0, date = LocalDate.of(2026, 11, 1))
        val before = expense(1.0, date = LocalDate.of(2026, 10, 31))
        val after = expense(1.0, date = LocalDate.of(2026, 12, 1))
        assertEquals(listOf(inside), listOf(before, inside, after).inMonth(YearMonth.of(2026, 11)))
    }

    @Test
    fun `totals are converted to EUR`() {
        assertEquals(10.0 + 0.92 * 5, listOf(expense(10.0), expense(5.0, "USD")).totalEur(), 1e-9)
    }

    @Test
    fun `byCategory groups, puts uncategorised under Other and sorts biggest first`() {
        val breakdown = listOf(
            expense(5.0, category = "Health"),
            expense(20.0, category = "Groceries"),
            expense(3.0),
            expense(10.0, category = "Health"),
        ).byCategory()
        assertEquals(listOf("Groceries" to 20.0, "Health" to 15.0, "Other" to 3.0), breakdown)
    }

    @Test
    fun `global budget is the one without a category`() {
        val global = Budget(1, null, 1500.0)
        assertEquals(global, listOf(Budget(2, "Groceries", 300.0), global).global())
        assertNull(listOf(Budget(2, "Groceries", 300.0)).global())
    }

    @Test
    fun `remaining is expressed in the budget currency`() {
        val budget = Budget(1, null, 100.0, "USD")
        assertEquals(92.0, budget.amountEur(), 1e-9)
        // 46 EUR spent = 50 USD, so half of the USD budget is left.
        assertEquals(50.0, budget.remaining(46.0), 1e-9)
        assertEquals(-50.0, budget.remaining(138.0), 1e-9)
    }

    @Test
    fun `daysUntilNext is negative when overdue`() {
        val sub = Subscription(1, "Netflix", 10.0, nextDate = today.minusDays(2))
        assertEquals(-2L, sub.daysUntilNext(today))
        assertEquals(3L, sub.copy(nextDate = today.plusDays(3)).daysUntilNext(today))
    }

    @Test
    fun `monthlyEur normalises every period to a month`() {
        val sub = Subscription(1, "x", 12.0, nextDate = today)
        assertEquals(12.0, sub.copy(period = Period.MONTHLY).monthlyEur(), 1e-9)
        assertEquals(1.0, sub.copy(period = Period.YEARLY).monthlyEur(), 1e-9)
        assertEquals(52.0, sub.copy(period = Period.WEEKLY).monthlyEur(), 1e-9)
    }
}
