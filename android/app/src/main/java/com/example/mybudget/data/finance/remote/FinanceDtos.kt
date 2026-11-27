package com.example.mybudget.data.finance.remote

import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.CurrencyConverter
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Period
import com.example.mybudget.ui.model.Subscription
import kotlinx.serialization.Serializable
import java.time.LocalDate

// DTOs of the server CRUD routes (server/src/index.ts, tables in server/schema.sql).
// Dates are ISO strings ("2026-11-24"), amounts are JSON numbers.
// Request bodies always carry every field, nulls included, so that a PUT can also clear a field
// (the server only updates the fields present in the body).

@Serializable
data class ExpenseDto(
    val id: Long,
    val title: String,
    val amount: Double,
    val currency: String,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val date: String,
    /** Home-currency units for 1 unit of [currency] on [date], set by the server. Null until it could reach the rate API. */
    val rate: Double? = null,
    /** Set when the server created this expense by charging a subscription. */
    val subscriptionId: Long? = null,
)

@Serializable
data class ExpenseBody(
    val title: String,
    val amount: Double,
    val currency: String,
    val category: String?,
    val tags: List<String>,
    val date: String,
)

@Serializable
data class SubscriptionDto(
    val id: Long,
    val name: String,
    val amount: Double,
    val currency: String,
    val category: String? = null,
    val period: String,
    val nextDate: String,
)

@Serializable
data class SubscriptionBody(
    val name: String,
    val amount: Double,
    val currency: String,
    val category: String?,
    val period: String,
    val nextDate: String,
)

@Serializable
data class BudgetDto(
    val id: Long,
    val category: String? = null,
    val amount: Double,
    val currency: String,
)

@Serializable
data class BudgetBody(
    val category: String?,
    val amount: Double,
    val currency: String,
)

@Serializable
data class CategoryDto(val id: Long, val name: String, val color: String, val icon: String)

@Serializable
data class CategoryBody(val name: String, val color: String, val icon: String)

fun CategoryDto.toModel() = CustomCategory(id, name, color, icon)

fun CustomCategory.toDto() = CategoryDto(id, name, color, icon)

fun CustomCategory.toBody() = CategoryBody(name, color, icon)

/** `GET /me`, `PUT /me`. */
@Serializable
data class ProfileDto(val homeCurrency: String)

@Serializable
data class ProfileBody(val homeCurrency: String)

/** `GET /rates`: today's rates, as [base] (home currency) units for 1 unit of each currency. */
@Serializable
data class RatesDto(val base: String, val rates: Map<String, Double>)

fun ExpenseDto.toModel() = Expense(id, title, amount, currency, category, tags, LocalDate.parse(date), rate, subscriptionId)

fun Expense.toDto() = ExpenseDto(id, title, amount, currency, category, tags, date.toString(), rate, subscriptionId)

fun Expense.toBody() = ExpenseBody(title, amount, currency, category, tags, date.toString())

fun SubscriptionDto.toModel() = Subscription(
    id = id,
    name = name,
    amount = amount,
    currency = currency,
    category = category,
    period = Period.entries.firstOrNull { it.apiValue == period } ?: Period.MONTHLY,
    nextDate = LocalDate.parse(nextDate),
)

fun Subscription.toBody() = SubscriptionBody(name, amount, currency, category, period.apiValue, nextDate.toString())

fun Subscription.toDto() = SubscriptionDto(id, name, amount, currency, category, period.apiValue, nextDate.toString())

fun BudgetDto.toModel() = Budget(id, category, amount, currency)

fun Budget.toBody() = BudgetBody(category, amount, currency)

fun Budget.toDto() = BudgetDto(id, category, amount, currency)

fun RatesDto.toModel() = CurrencyConverter(base, rates)
