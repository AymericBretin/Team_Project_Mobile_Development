package com.example.mybudget.ui.model

import java.time.LocalDate

/**
 * UI models. Field names mirror the server tables (server/schema.sql) so that the data layer
 * (Aymeric) can map DTOs 1:1. amount is a Double for now; switch to BigDecimal if needed.
 */
data class Expense(
    val id: Long,
    val title: String,
    val amount: Double,
    val currency: String = "EUR",
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val date: LocalDate = LocalDate.now(),
)

data class Subscription(
    val id: Long,
    val name: String,
    val amount: Double,
    val currency: String = "EUR",
    val category: String? = null,
    val period: Period = Period.MONTHLY,
    val nextDate: LocalDate,
)

enum class Period(val label: String, val apiValue: String) {
    WEEKLY("Weekly", "weekly"),
    MONTHLY("Monthly", "monthly"),
    YEARLY("Yearly", "yearly"),
}

/** category == null means the global monthly budget. */
data class Budget(
    val id: Long,
    val category: String?,
    val amount: Double,
    val currency: String = "EUR",
)

val DefaultCategories = listOf(
    "Groceries", "Restaurant", "Transport", "Subscriptions",
    "Shopping", "Health", "Leisure", "Travel", "Other",
)

val SupportedCurrencies = listOf("EUR", "KRW", "USD", "GBP", "JPY")
