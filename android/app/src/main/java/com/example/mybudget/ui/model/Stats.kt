package com.example.mybudget.ui.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Placeholder exchange rates (to EUR) so totals work across currencies in the UI.
 * TODO(back-end): replace with the exchange-rate API (multi-currency feature, week 13).
 */
object ExchangeRates {
    private val toEur = mapOf("EUR" to 1.0, "KRW" to 0.000664, "USD" to 0.92, "GBP" to 1.17, "JPY" to 0.0061)
    fun toEur(amount: Double, currency: String): Double = amount * (toEur[currency] ?: 1.0)
    fun fromEur(amount: Double, currency: String): Double = amount / (toEur[currency] ?: 1.0)
}

fun Expense.amountEur() = ExchangeRates.toEur(amount, currency)

fun Budget.amountEur() = ExchangeRates.toEur(amount, currency)

/** What is left of this budget once [spentEur] is spent, in the budget's own currency. Negative when over budget. */
fun Budget.remaining(spentEur: Double) = amount - ExchangeRates.fromEur(spentEur, currency)

fun List<Expense>.inMonth(month: YearMonth) = filter { YearMonth.from(it.date) == month }

fun List<Expense>.totalEur() = sumOf { it.amountEur() }

/** Category -> total in EUR, biggest first. Uncategorised expenses go under "Other". */
fun List<Expense>.byCategory(): List<Pair<String, Double>> =
    groupBy { it.category ?: "Other" }
        .map { (cat, items) -> cat to items.totalEur() }
        .sortedByDescending { it.second }

fun Budget.isGlobal() = category == null

fun List<Budget>.global(): Budget? = firstOrNull { it.isGlobal() }

fun Subscription.daysUntilNext(today: LocalDate = LocalDate.now()): Long =
    java.time.temporal.ChronoUnit.DAYS.between(today, nextDate)

/** Cost per month in EUR, to show "your subscriptions cost X / month". */
fun Subscription.monthlyEur(): Double {
    val eur = ExchangeRates.toEur(amount, currency)
    return when (period) {
        Period.WEEKLY -> eur * 52.0 / 12.0
        Period.MONTHLY -> eur
        Period.YEARLY -> eur / 12.0
    }
}
