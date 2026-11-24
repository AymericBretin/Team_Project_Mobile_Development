package com.example.mybudget.ui.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * TEMPORARY in-memory store so the screens are fully usable before the API layer exists.
 *
 * Integration point for the back-end teammate: keep these function names and the three StateFlows,
 * and replace the bodies with calls to `container.authenticatedRetrofit.create<ExpensesApi>()` etc.
 * (GET/POST/PUT/DELETE on /expenses, /subscriptions, /budgets). The UI won't need to change.
 */
class BudgetViewModel : ViewModel() {

    private val today = LocalDate.now()

    private val _expenses = MutableStateFlow(
        listOf(
            Expense(1, "Groceries", 45.0, "EUR", "Groceries", listOf("weekly"), today),
            Expense(2, "Restaurant", 18.0, "EUR", "Restaurant", emptyList(), today),
            Expense(3, "Ramen, Seoul", 9000.0, "KRW", "Restaurant", listOf("seoul"), today.minusDays(1)),
            Expense(4, "Metro card", 20.0, "EUR", "Transport", emptyList(), today.minusDays(3)),
            Expense(5, "Pharmacy", 12.5, "EUR", "Health", emptyList(), today.minusDays(5)),
        ),
    )
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _subscriptions = MutableStateFlow(
        listOf(
            Subscription(1, "Netflix", 13.99, "EUR", "Subscriptions", Period.MONTHLY, today.plusDays(6)),
            Subscription(2, "Spotify", 9.99, "EUR", "Subscriptions", Period.MONTHLY, today.plusDays(12)),
        ),
    )
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _budgets = MutableStateFlow(
        listOf(
            Budget(1, null, 1500.0),
            Budget(2, "Restaurant", 150.0),
            Budget(3, "Groceries", 300.0),
        ),
    )
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    private var nextId = 100L

    fun saveExpense(expense: Expense) = _expenses.update { list ->
        if (list.any { it.id == expense.id }) list.map { if (it.id == expense.id) expense else it }
        else list + expense.copy(id = nextId++)
    }

    fun deleteExpense(id: Long) = _expenses.update { list -> list.filterNot { it.id == id } }

    fun saveSubscription(subscription: Subscription) = _subscriptions.update { list ->
        if (list.any { it.id == subscription.id }) list.map { if (it.id == subscription.id) subscription else it }
        else list + subscription.copy(id = nextId++)
    }

    fun deleteSubscription(id: Long) = _subscriptions.update { list -> list.filterNot { it.id == id } }

    fun saveBudget(budget: Budget) = _budgets.update { list ->
        if (list.any { it.id == budget.id }) list.map { if (it.id == budget.id) budget else it }
        else list + budget.copy(id = nextId++)
    }

    fun deleteBudget(id: Long) = _budgets.update { list -> list.filterNot { it.id == id } }
}
