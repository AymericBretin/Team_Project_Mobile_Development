package com.example.mybudget.ui.components

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * The app is in English only (international course), whatever the phone's language: dates, month names and amounts
 * are always formatted with this locale, and MainActivity runs with it so Material components (date pickers…) match.
 */
val AppLocale: Locale = Locale.US

/** "€1,245.00", "₩9,000" (no decimals for currencies that don't use them). */
fun formatMoney(amount: Double, currency: String): String {
    val format = NumberFormat.getCurrencyInstance(AppLocale)
    runCatching { format.currency = Currency.getInstance(currency) }
    val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2)
    format.minimumFractionDigits = digits.coerceAtLeast(0)
    format.maximumFractionDigits = digits.coerceAtLeast(0)
    return format.format(amount)
}

fun formatDay(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", AppLocale))
    }
}
