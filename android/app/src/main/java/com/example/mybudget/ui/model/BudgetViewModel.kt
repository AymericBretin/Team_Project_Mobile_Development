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
import com.example.mybudget.data.finance.local.FinanceCache
import com.example.mybudget.data.finance.local.FinanceData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Something to tell the user once (snackbar). */
sealed interface UserMessage {
    /** A server call failed. [isLoadError]: loading the data failed, offer to retry. */
    data class Failure(val error: DataError, val isLoadError: Boolean = false) : UserMessage

    /** The expense just saved pushed a budget past 80 % or past 100 %. */
    data class BudgetWarning(val alert: BudgetAlert) : UserMessage
}

/**
 * Expenses, subscriptions, budgets and currency settings of the signed-in user, loaded from the server.
 *
 * The last loaded data is kept in [cache] and shown right away on the next start, so the app is usable
 * offline (read-only). Saves and deletes go to the server first, the lists are updated once it has answered
 * (so new items get their real id and exchange rate). Failures are reported through [message].
 */
class BudgetViewModel(
    private val repository: FinanceRepository,
    private val cache: FinanceCache = FinanceCache.None,
    private val userId: String = "",
) : ViewModel() {

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    /** Categories the user created, on top of [DefaultCategories]. */
    private val _categories = MutableStateFlow<List<CustomCategory>>(emptyList())
    val categories: StateFlow<List<CustomCategory>> = _categories.asStateFlow()

    /** Home currency and today's rates: every total is shown in [CurrencyConverter.home]. */
    private val _converter = MutableStateFlow(CurrencyConverter())
    val converter: StateFlow<CurrencyConverter> = _converter.asStateFlow()

    /** A full reload is running (first load, pull-to-refresh or home currency change). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val pendingCalls = MutableStateFlow(0)

    /** Any call to the server is running (reload, save or delete). */
    val isBusy: StateFlow<Boolean> = pendingCalls.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    init {
        _isRefreshing.value = true
        track {
            cache.load(userId)?.let { cached ->
                _converter.value = cached.converter
                _expenses.value = cached.expenses
                _subscriptions.value = cached.subscriptions
                _budgets.value = cached.budgets
                _categories.value = cached.categories
            }
            reload()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        track { reload() }
    }

    fun saveExpense(expense: Expense) = track {
        repository.saveExpense(expense).onSuccess { saved ->
            val before = _expenses.value
            _expenses.update { it.upsert(saved, Expense::id) }
            budgetAlert(_budgets.value, before, _expenses.value, YearMonth.from(saved.date), _converter.value)
                ?.let { _message.value = UserMessage.BudgetWarning(it) }
        }
    }

    fun deleteExpense(id: Long) = track {
        repository.deleteExpense(id).onSuccess { _expenses.update { list -> list.filterNot { it.id == id } } }
    }

    fun saveSubscription(subscription: Subscription) = track {
        repository.saveSubscription(subscription).onSuccess { saved ->
            _subscriptions.update { it.upsert(saved, Subscription::id) }
        }
        // A subscription whose next payment is today or earlier has just been charged as expenses by the server.
        repository.expenses().ifSuccess { _expenses.value = it }
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

    fun saveCategory(category: CustomCategory) = track {
        val renamed = _categories.value.firstOrNull { it.id == category.id }?.let { it.name != category.name } == true
        repository.saveCategory(category).onSuccess { saved -> _categories.update { it.upsert(saved, CustomCategory::id) } }
        // The server renamed it on every expense, subscription and budget.
        if (renamed) reloadLists()
    }

    fun deleteCategory(id: Long) = track {
        repository.deleteCategory(id).onSuccess { _categories.update { list -> list.filterNot { it.id == id } } }
        // Its expenses and subscriptions are now uncategorised and its budget is gone.
        reloadLists()
    }

    /** The server converts every expense again, so everything is reloaded afterwards. */
    fun setHomeCurrency(currency: String) {
        if (currency == _converter.value.home || _isRefreshing.value) return
        _isRefreshing.value = true
        track {
            when (val result = repository.setHomeCurrency(currency)) {
                is DataResult.Success -> reload()
                is DataResult.Failure -> {
                    report(result.error, isLoadError = false)
                    _isRefreshing.value = false
                }
            }
        }
    }

    /** Call once the [message] has been shown. */
    fun messageShown() {
        _message.value = null
    }

    /** Loads everything from the server. Ends the refresh started by the caller. */
    private suspend fun reload() = coroutineScope {
        val expenses = async { repository.expenses() }
        val subscriptions = async { repository.subscriptions() }
        val budgets = async { repository.budgets() }
        val home = async { repository.homeCurrency() }
        val rates = async { repository.rates() }
        val categories = async { repository.categories() }

        var newHome: String? = null
        val errors = listOfNotNull(
            home.await().ifSuccess { newHome = it },
            expenses.await().ifSuccess { _expenses.value = it },
            subscriptions.await().ifSuccess { _subscriptions.value = it },
            budgets.await().ifSuccess { _budgets.value = it },
            categories.await().ifSuccess { _categories.value = it },
        )
        // Rates are optional: without them the app keeps the last known ones (cached).
        val fresh = (rates.await() as? DataResult.Success)?.data
        val current = _converter.value
        val homeCurrency = newHome ?: current.home
        _converter.value = when {
            fresh != null && fresh.home == homeCurrency -> fresh
            current.home == homeCurrency -> current
            else -> CurrencyConverter(homeCurrency)
        }

        // One message for all the lists.
        errors.firstOrNull()?.let { report(it, isLoadError = true) }
        _isRefreshing.value = false
    }

    /** Reloads what a category change touched on the server. Failures are not reported: the change itself went through. */
    private suspend fun reloadLists() = coroutineScope {
        val expenses = async { repository.expenses() }
        val subscriptions = async { repository.subscriptions() }
        val budgets = async { repository.budgets() }
        expenses.await().ifSuccess { _expenses.value = it }
        subscriptions.await().ifSuccess { _subscriptions.value = it }
        budgets.await().ifSuccess { _budgets.value = it }
    }

    /** Runs a server call, then saves what is shown, so the next start (even offline) shows the same. */
    private fun track(block: suspend CoroutineScope.() -> Unit) {
        pendingCalls.update { it + 1 }
        viewModelScope.launch {
            try {
                block()
                cache.save(
                    userId,
                    FinanceData(_converter.value, _expenses.value, _subscriptions.value, _budgets.value, _categories.value),
                )
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
        _message.value = UserMessage.Failure(error, isLoadError)
    }

    companion object {
        /** One ViewModel per user: its cache and data belong to that user only. */
        fun factory(userId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MyBudgetApplication
                BudgetViewModel(app.container.financeRepository, app.container.financeCache, userId)
            }
        }
    }
}

/** Replaces the item with the same id, or appends it. */
private fun <T> List<T>.upsert(item: T, id: (T) -> Long): List<T> =
    if (any { id(it) == id(item) }) map { if (id(it) == id(item)) item else it } else this + item
