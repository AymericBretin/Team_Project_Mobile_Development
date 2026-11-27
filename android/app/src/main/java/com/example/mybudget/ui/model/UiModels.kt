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
    /** Home-currency units for 1 unit of [currency] on [date] (rate saved at purchase). Null while unknown. */
    val rate: Double? = null,
    /** The subscription this expense was charged for, if the server created it automatically. */
    val subscriptionId: Long? = null,
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

/**
 * A category the user created, on top of [DefaultCategories]. [color] and [icon] are keys of the app's palettes
 * (see ui/components/Categories.kt), so the server never deals with UI values.
 */
data class CustomCategory(
    val id: Long,
    val name: String,
    val color: String,
    val icon: String,
)

/** Built-in categories, available to everyone. They can't be renamed or deleted. */
val DefaultCategories = listOf(
    "Groceries", "Restaurant", "Transport", "Subscriptions",
    "Shopping", "Health", "Leisure", "Travel", "Other",
)

/** The currencies the exchange-rate API knows (ECB reference rates), the most used ones first. */
val SupportedCurrencies = listOf("EUR", "KRW", "USD", "GBP", "JPY") + listOf(
    "AUD", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "HKD", "HUF", "IDR", "ILS", "INR", "ISK",
    "MXN", "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB", "TRY", "ZAR",
)
