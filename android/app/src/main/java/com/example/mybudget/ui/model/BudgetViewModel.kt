package com.example.mybudget.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mybudget.MyBudgetApplication
import com.example.mybudget.data.finance.DataError
import com.example.mybudget.data.finance.DataResult
import com.example.mybudget.data.finance.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Error to show once (snackbar). [isLoadError]: loading the data failed, offer to retry. */
data class UserMessage(val error: DataError, val isLoadError: Boolean = false)

/**
 * Expenses, subscriptions and budgets of the signed-in user, loaded from the server.
 *
 * Saves and deletes go to the server first, the lists are updated once it has answered
 * (so new items get their real id). Failures are reported through [message].
 */
class BudgetViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    /** A full reload is running (first load or pull-to-refresh). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val pendingCalls = MutableStateFlow(0)

    /** Any call to the server is running (reload, save or delete). */
    val isBusy: StateFlow<Boolean> = pendingCalls.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        track {
            val expenses = async { repository.expenses() }
            val subscriptions = async { repository.subscriptions() }
            val budgets = async { repository.budgets() }
            val errors = listOfNotNull(
                expenses.await().ifSuccess { _expenses.value = it },
                subscriptions.await().ifSuccess { _subscriptions.value = it },
                budgets.await().ifSuccess { _budgets.value = it },
            )
            // One message for the three lists.
            errors.firstOrNull()?.let { report(it, isLoadError = true) }
            _isRefreshing.value = false
        }
    }

    fun saveExpense(expense: Expense) = track {
        repository.saveExpense(expense).onSuccess { saved -> _expenses.update { it.upsert(saved, Expense::id) } }
    }

    fun deleteExpense(id: Long) = track {
        repository.deleteExpense(id).onSuccess { _expenses.update { list -> list.filterNot { it.id == id } } }
    }

    fun saveSubscription(subscription: Subscription) = track {
        repository.saveSubscription(subscription).onSuccess { saved ->
            _subscriptions.update { it.upsert(saved, Subscription::id) }
        }
    }

    fun deleteSubscription(id: Long) = track {
        repository.deleteSubscription(id).onSuccess { _subscriptions.update { list -> list.filterNot { it.id == id } } }
    }

    fun saveBudget(budget: Budget) = track {
        repository.saveBudget(budget).onSuccess { saved -> _budgets.update { it.upsert(saved, Budget::id) } }
    }

    fun deleteBudget(id: Long) = track {
        repository.deleteBudget(id).onSuccess { _budgets.update { list -> list.filterNot { it.id == id } } }
    }

    /** Call once the [message] has been shown. */
    fun messageShown() {
        _message.value = null
    }

    private fun track(block: suspend CoroutineScope.() -> Unit) {
        pendingCalls.update { it + 1 }
        viewModelScope.launch {
            try {
                block()
            } finally {
                pendingCalls.update { it - 1 }
            }
        }
    }

    /** Runs [action] on success and returns null, or returns the error. */
    private fun <T> DataResult<T>.ifSuccess(action: (T) -> Unit): DataError? = when (this) {
        is DataResult.Success -> { action(data); null }
        is DataResult.Failure -> error
    }

    /** For saves and deletes: runs [action] on success, reports the error otherwise. */
    private fun <T> DataResult<T>.onSuccess(action: (T) -> Unit) {
        ifSuccess(action)?.let { report(it, isLoadError = false) }
    }

    private fun report(error: DataError, isLoadError: Boolean) {
        // The session is gone: the app is already going back to the login screen, nothing to show here.
        if (error == DataError.SessionExpired) return
        _message.value = UserMessage(error, isLoadError)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MyBudgetApplication
                BudgetViewModel(app.container.financeRepository)
            }
        }
    }
}

/** Replaces the item with the same id, or appends it. */
private fun <T> List<T>.upsert(item: T, id: (T) -> Long): List<T> =
    if (any { id(it) == id(item) }) map { if (id(it) == id(item)) item else it } else this + item
