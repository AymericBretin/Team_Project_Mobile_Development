package com.example.mybudget.data.finance.local

import com.example.mybudget.data.finance.remote.BudgetDto
import com.example.mybudget.data.finance.remote.CategoryDto
import com.example.mybudget.data.finance.remote.ExpenseDto
import com.example.mybudget.data.finance.remote.SubscriptionDto
import com.example.mybudget.data.finance.remote.toDto
import com.example.mybudget.data.finance.remote.toModel
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.CurrencyConverter
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Everything the main screens show, kept on the device so the app still shows the user's data offline. */
data class FinanceData(
    val converter: CurrencyConverter,
    val expenses: List<Expense>,
    val subscriptions: List<Subscription>,
    val budgets: List<Budget>,
    val categories: List<CustomCategory> = emptyList(),
)

/** Last data loaded from the server, per user. Read-only offline: changes still need the server. */
interface FinanceCache {
    suspend fun load(userId: String): FinanceData?
    suspend fun save(userId: String, data: FinanceData)

    /** Called when the user signs out (or the session expires), so nothing stays on the device. */
    suspend fun clear()

    /** No cache (tests, previews). */
    object None : FinanceCache {
        override suspend fun load(userId: String): FinanceData? = null
        override suspend fun save(userId: String, data: FinanceData) = Unit
        override suspend fun clear() = Unit
    }
}

/**
 * JSON file per user in [dir]. Use `Context.noBackupFilesDir`: like the session, the cache must not be
 * restored onto another device.
 */
class FileFinanceCache(private val dir: File, private val json: Json) : FinanceCache {

    private val mutex = Mutex()

    @Serializable
    private data class Snapshot(
        val homeCurrency: String,
        val rates: Map<String, Double>,
        val expenses: List<ExpenseDto>,
        val subscriptions: List<SubscriptionDto>,
        val budgets: List<BudgetDto>,
        // Default: cache files written before custom categories existed.
        val categories: List<CategoryDto> = emptyList(),
    )

    private fun file(userId: String) = File(dir, "$PREFIX${userId.filter { it.isLetterOrDigit() }}.json")

    override suspend fun load(userId: String): FinanceData? = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Missing or unreadable (e.g. written by an older version of the app): start from the server.
            runCatching {
                val snapshot = json.decodeFromString(Snapshot.serializer(), file(userId).readText())
                FinanceData(
                    converter = CurrencyConverter(snapshot.homeCurrency, snapshot.rates),
                    expenses = snapshot.expenses.map { it.toModel() },
                    subscriptions = snapshot.subscriptions.map { it.toModel() },
                    budgets = snapshot.budgets.map { it.toModel() },
                    categories = snapshot.categories.map { it.toModel() },
                )
            }.getOrNull()
        }
    }

    override suspend fun save(userId: String, data: FinanceData) = withContext(Dispatchers.IO) {
        val snapshot = Snapshot(
            homeCurrency = data.converter.home,
            rates = data.converter.rates,
            expenses = data.expenses.map { it.toDto() },
            subscriptions = data.subscriptions.map { it.toDto() },
            budgets = data.budgets.map { it.toDto() },
            categories = data.categories.map { it.toDto() },
        )
        mutex.withLock {
            dir.mkdirs()
            // Write then rename, so a crash mid-write never leaves a half-written cache behind.
            val tmp = File(dir, "${file(userId).name}.tmp")
            tmp.writeText(json.encodeToString(Snapshot.serializer(), snapshot))
            if (!tmp.renameTo(file(userId))) tmp.delete()
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles { f -> f.name.startsWith(PREFIX) }?.forEach { it.delete() }
            Unit
        }
    }

    private companion object {
        const val PREFIX = "finance-"
    }
}
