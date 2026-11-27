package com.example.mybudget.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val eur = CurrencyConverter("EUR", mapOf("USD" to 0.9, "KRW" to 0.0007))

    private fun expense(amount: Double, currency: String = "EUR", category: String? = null, date: LocalDate = today, rate: Double? = null) =
        Expense(id = 0, title = "x", amount = amount, currency = currency, category = category, date = date, rate = rate)

    @Test
    fun `converts with today's rates, in both directions`() {
        assertEquals(9.0, eur.toHome(10.0, "USD"), 1e-9)
        assertEquals(10.0, eur.fromHome(9.0, "USD"), 1e-9)
        assertEquals(10.0, eur.toHome(10.0, "EUR"), 1e-9)
    }

    @Test
    fun `without server rates, falls back to approximate ones, then to 1 to 1`() {
        val offline = CurrencyConverter("EUR")
        assertEquals(0.91, offline.rate("USD")!!, 1e-9)
        assertEquals(10.0, offline.toHome(10.0, "CHF"), 1e-9)
        assertEquals(0.91 / 0.00068, CurrencyConverter("KRW").rate("USD")!!, 1e-6)
    }

    @Test
    fun `an expense uses the rate of its own day when the server has it`() {
        assertEquals(6.0, expense(9000.0, "KRW", rate = 1 / 1500.0).homeAmount(eur), 1e-9)
        assertEquals(6.3, expense(9000.0, "KRW").homeAmount(eur), 1e-9)
        assertEquals(5.0, expense(5.0, "EUR", rate = null).homeAmount(eur), 1e-9)
    }

    @Test
    fun `inMonth keeps only the expenses of that month`() {
        val inside = expense(1.0, date = LocalDate.of(2026, 11, 1))
        val before = expense(1.0, date = LocalDate.of(2026, 10, 31))
        val after = expense(1.0, date = LocalDate.of(2026, 12, 1))
        assertEquals(listOf(inside), listOf(before, inside, after).inMonth(YearMonth.of(2026, 11)))
    }

    @Test
    fun `totals are converted to the home currency`() {
        assertEquals(10.0 + 0.9 * 5, listOf(expense(10.0), expense(5.0, "USD")).total(eur), 1e-9)
    }

    @Test
    fun `byCategory groups, puts uncategorised under Other and sorts biggest first`() {
        val breakdown = listOf(
            expense(5.0, category = "Health"),
            expense(20.0, category = "Groceries"),
            expense(3.0),
            expense(10.0, category = "Health"),
        ).byCategory(eur)
        assertEquals(listOf("Groceries" to 20.0, "Health" to 15.0, "Other" to 3.0), breakdown)
    }

    @Test
    fun `monthlyTotals lists every month, empty ones included, oldest first`() {
        val totals = listOf(
            expense(10.0, date = LocalDate.of(2026, 11, 2)),
            expense(5.0, date = LocalDate.of(2026, 11, 20)),
            expense(7.0, date = LocalDate.of(2026, 9, 1)),
            expense(99.0, date = LocalDate.of(2026, 5, 1)),
        ).monthlyTotals(eur, YearMonth.of(2026, 11), 3)
        assertEquals(listOf(YearMonth.of(2026, 9) to 7.0, YearMonth.of(2026, 10) to 0.0, YearMonth.of(2026, 11) to 15.0), totals)
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
        assertEquals(90.0, budget.homeAmount(eur), 1e-9)
        // 45 EUR spent = 50 USD, so half of the USD budget is left.
        assertEquals(50.0, budget.remaining(45.0, eur), 1e-9)
        assertEquals(-50.0, budget.remaining(135.0, eur), 1e-9)
    }

    @Test
    fun `daysUntilNext is negative when overdue`() {
        val sub = Subscription(1, "Netflix", 10.0, nextDate = today.minusDays(2))
        assertEquals(-2L, sub.daysUntilNext(today))
        assertEquals(3L, sub.copy(nextDate = today.plusDays(3)).daysUntilNext(today))
    }

    @Test
    fun `monthly normalises every period to a month, in the home currency`() {
        val sub = Subscription(1, "x", 12.0, nextDate = today)
        assertEquals(12.0, sub.copy(period = Period.MONTHLY).monthly(eur), 1e-9)
        assertEquals(1.0, sub.copy(period = Period.YEARLY).monthly(eur), 1e-9)
        assertEquals(52.0, sub.copy(period = Period.WEEKLY).monthly(eur), 1e-9)
        assertEquals(10.8, sub.copy(currency = "USD").monthly(eur), 1e-9)
    }

    @Test
    fun `budget usage counts the month's expenses of its category, or all of them for the global budget`() {
        val expenses = listOf(
            expense(30.0, category = "Restaurant"),
            expense(10.0, "USD", category = "Restaurant"),
            expense(50.0, category = "Groceries"),
            expense(99.0, category = "Restaurant", date = today.minusMonths(1)),
        )
        val month = YearMonth.from(today)

        val restaurant = Budget(1, "Restaurant", 50.0).usage(expenses, month, eur)
        assertEquals(39.0, restaurant.spentHome, 1e-9)
        assertEquals(0.78, restaurant.ratio, 1e-9)
        assertEquals(false, restaurant.isNearLimit)
        assertEquals(0.89, Budget(2, null, 100.0).usage(expenses, month, eur).ratio, 1e-9)
    }

    @Test
    fun `an alert is raised only when a threshold is crossed, the most serious one first`() {
        val month = YearMonth.from(today)
        val budgets = listOf(Budget(1, null, 100.0), Budget(2, "Restaurant", 50.0))
        val before = listOf(expense(35.0, category = "Restaurant"))

        val near = budgetAlert(budgets, before, before + expense(6.0, category = "Restaurant"), month, eur)!!
        assertEquals("Restaurant", near.usage.budget.category)
        assertEquals(false, near.usage.isOver)

        val bigOne = budgetAlert(budgets, before, before + expense(70.0, category = "Restaurant"), month, eur)!!
        assertEquals("both budgets crossed: the category one, over its limit, comes first", "Restaurant", bigOne.usage.budget.category)
        assertEquals(true, bigOne.usage.isOver)

        assertNull(budgetAlert(budgets, before, before + expense(1.0, category = "Health"), month, eur))
        assertNull("lowering an expense never alerts", budgetAlert(budgets, before + expense(70.0), before, month, eur))
    }
}

