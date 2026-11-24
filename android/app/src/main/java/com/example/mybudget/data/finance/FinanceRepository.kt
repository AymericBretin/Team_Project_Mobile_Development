package com.example.mybudget.data.finance

import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Subscription

/**
 * Expenses, subscriptions and budgets of the signed-in user, stored on the server.
 *
 * `save*` creates the item when its id is [NEW_ID] and updates it otherwise, and returns it as stored
 * by the server (with its real id).
 */
interface FinanceRepository {
    suspend fun expenses(): DataResult<List<Expense>>
    suspend fun saveExpense(expense: Expense): DataResult<Expense>
    suspend fun deleteExpense(id: Long): DataResult<Unit>

    suspend fun subscriptions(): DataResult<List<Subscription>>
    suspend fun saveSubscription(subscription: Subscription): DataResult<Subscription>
    suspend fun deleteSubscription(id: Long): DataResult<Unit>

    suspend fun budgets(): DataResult<List<Budget>>
    suspend fun saveBudget(budget: Budget): DataResult<Budget>
    suspend fun deleteBudget(id: Long): DataResult<Unit>

    companion object {
        /** Id of an item that is not on the server yet. */
        const val NEW_ID = 0L
    }
}

/** Outcome of a server call: either a value or a typed [DataError]. */
sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>

    data class Failure(val error: DataError) : DataResult<Nothing>
}

/** Errors the UI can map to user-facing messages. */
sealed interface DataError {
    /** The server refused the data (400). [message] comes from the server and is user-readable. */
    data class Validation(val message: String?) : DataError

    /** The item was deleted meanwhile (e.g. from another device). */
    data object NotFound : DataError

    /** Still 401 after the automatic refresh: the user is being signed out. */
    data object SessionExpired : DataError

    data object Server : DataError
    data object Network : DataError
    data class Unknown(val message: String?) : DataError
}
