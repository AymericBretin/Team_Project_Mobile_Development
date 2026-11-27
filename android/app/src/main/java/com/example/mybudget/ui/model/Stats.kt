package com.example.mybudget.ui.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Converts amounts to the user's home currency, in which every total is shown.
 *
 * [rates] come from the server (`GET /rates`): [home] units for 1 unit of each currency, at today's rate.
 * Expenses use the rate saved on the day they were made instead (see [homeAmount]).
 */
data class CurrencyConverter(val home: String = "EUR", val rates: Map<String, Double> = emptyMap()) {

    /** [home] units for 1 [currency] today, or null when unknown. */
    fun rate(currency: String): Double? =
        if (currency == home) 1.0 else rates[currency] ?: ApproximateRates.cross(currency, home)

    /** Unknown currencies count 1:1, so that totals still show something. */
    fun toHome(amount: Double, currency: String): Double = amount * (rate(currency) ?: 1.0)

    fun fromHome(amount: Double, currency: String): Double = amount / (rate(currency) ?: 1.0)
}

/**
 * Rough rates in EUR, only used while the app has never loaded real ones (first launch without network),
 * so that totals mixing currencies are not completely off.
 */
private object ApproximateRates {
    private val eurPerUnit = mapOf("EUR" to 1.0, "KRW" to 0.00068, "USD" to 0.91, "GBP" to 1.19, "JPY" to 0.0057)

    fun cross(from: String, to: String): Double? {
        val fromEur = eurPerUnit[from] ?: return null
        val toEur = eurPerUnit[to] ?: return null
        return fromEur / toEur
    }
}

/** Amount in the home currency, at the rate of the day of the expense when the server has it. */
fun Expense.homeAmount(converter: CurrencyConverter): Double =
    if (currency == converter.home) amount else rate?.let { amount * it } ?: converter.toHome(amount, currency)

fun Budget.homeAmount(converter: CurrencyConverter) = converter.toHome(amount, currency)

/** What is left of this budget once [spentHome] is spent, in the budget's own currency. Negative when over budget. */
fun Budget.remaining(spentHome: Double, converter: CurrencyConverter) = amount - converter.fromHome(spentHome, currency)

fun List<Expense>.inMonth(month: YearMonth) = filter { YearMonth.from(it.date) == month }

fun List<Expense>.total(converter: CurrencyConverter) = sumOf { it.homeAmount(converter) }

/** Category -> total in the home currency, biggest first. Uncategorised expenses go under "Other". */
fun List<Expense>.byCategory(converter: CurrencyConverter): List<Pair<String, Double>> =
    groupBy { it.category ?: "Other" }
        .map { (cat, items) -> cat to items.total(converter) }
        .sortedByDescending { it.second }

/** Total spent in each of the [count] months ending with [last], oldest first. */
fun List<Expense>.monthlyTotals(converter: CurrencyConverter, last: YearMonth, count: Int): List<Pair<YearMonth, Double>> {
    val byMonth = groupBy { YearMonth.from(it.date) }
    return (count - 1 downTo 0).map { back ->
        val month = last.minusMonths(back.toLong())
        month to (byMonth[month]?.total(converter) ?: 0.0)
    }
}

fun Budget.isGlobal() = category == null

fun List<Budget>.global(): Budget? = firstOrNull { it.isGlobal() }

fun Subscription.daysUntilNext(today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, nextDate)

/** Cost per month in the home currency, to show "your subscriptions cost X / month". */
fun Subscription.monthly(converter: CurrencyConverter): Double {
    val home = converter.toHome(amount, currency)
    return when (period) {
        Period.WEEKLY -> home * 52.0 / 12.0
        Period.MONTHLY -> home
        Period.YEARLY -> home / 12.0
    }
}

/** Share of the budget to reach before warning the user. */
const val BUDGET_WARNING_RATIO = 0.8

/** How much of [budget] is spent in a month: [ratio] 1.0 = exactly the budget, above = over budget. */
data class BudgetUsage(val budget: Budget, val spentHome: Double, val ratio: Double) {
    val isOver get() = ratio >= 1.0
    val isNearLimit get() = ratio >= BUDGET_WARNING_RATIO
}

/** Spending in [month] against this budget: every expense for the global budget, its category's otherwise. */
fun Budget.usage(expenses: List<Expense>, month: YearMonth, converter: CurrencyConverter): BudgetUsage {
    val counted = expenses.inMonth(month).let { inMonth -> if (category == null) inMonth else inMonth.filter { it.category == category } }
    val spent = counted.total(converter)
    val limit = homeAmount(converter)
    return BudgetUsage(this, spent, if (limit > 0) spent / limit else 0.0)
}

/** A budget that went past a threshold (80 % or 100 %) because of a change in the expenses. */
data class BudgetAlert(val usage: BudgetUsage, val month: YearMonth)

/**
 * The budget of [month] that a change from [before] to [after] pushed past 80 % or past 100 %, if any.
 * When several did, the most serious one: over budget first, then a category budget rather than the global one.
 */
fun budgetAlert(
    budgets: List<Budget>,
    before: List<Expense>,
    after: List<Expense>,
    month: YearMonth,
    converter: CurrencyConverter,
): BudgetAlert? = budgets
    .mapNotNull { budget ->
        val was = budget.usage(before, month, converter)
        val now = budget.usage(after, month, converter)
        now.takeIf { (it.isOver && !was.isOver) || (it.isNearLimit && !was.isNearLimit) }
    }
    .sortedWith(compareByDescending<BudgetUsage> { it.isOver }.thenByDescending { it.budget.category != null }.thenByDescending { it.ratio })
    .firstOrNull()
    ?.let { BudgetAlert(it, month) }
