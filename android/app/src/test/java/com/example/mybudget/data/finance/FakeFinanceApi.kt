package com.example.mybudget.data.finance

import com.example.mybudget.data.finance.remote.BudgetBody
import com.example.mybudget.data.finance.remote.BudgetDto
import com.example.mybudget.data.finance.remote.CategoryBody
import com.example.mybudget.data.finance.remote.CategoryDto
import com.example.mybudget.data.finance.remote.ExpenseBody
import com.example.mybudget.data.finance.remote.ExpenseDto
import com.example.mybudget.data.finance.remote.FinanceApi
import com.example.mybudget.data.finance.remote.ProfileBody
import com.example.mybudget.data.finance.remote.ProfileDto
import com.example.mybudget.data.finance.remote.RatesDto
import com.example.mybudget.data.finance.remote.SubscriptionBody
import com.example.mybudget.data.finance.remote.SubscriptionDto

/** In-memory server: behaves like the routes of server/src/index.ts. [failWith] makes every call throw. */
class FakeFinanceApi : FinanceApi {
    val expenses = mutableListOf<ExpenseDto>()
    val subscriptions = mutableListOf<SubscriptionDto>()
    val budgets = mutableListOf<BudgetDto>()
    var homeCurrency = "EUR"
    /** Today's rates returned by `GET /rates`, to [homeCurrency]. */
    var rates = mapOf("EUR" to 1.0)
    var ratesUnavailable = false
    /** Rate the server gives each expense when the home currency changes. */
    var rateOf: (currency: String, home: String) -> Double? = { currency, home -> if (currency == home) 1.0 else null }
    /** Runs after a subscription is saved, e.g. to mimic the server charging a due one as an expense. */
    var onSubscriptionSaved: (SubscriptionDto) -> Unit = {}
    val categories = mutableListOf<CategoryDto>()
    /** Runs when a category is renamed, to mimic the server renaming it on the rows using it. */
    var onCategoryRenamed: (old: String, new: String) -> Unit = { _, _ -> }
    var failWith: Exception? = null
    var calls = mutableListOf<String>()
    private var nextId = 1L

    private fun <T> run(name: String, block: () -> T): T {
        calls += name
        failWith?.let { throw it }
        return block()
    }

    private fun notFound() = com.example.mybudget.data.auth.httpError(404, """{"code":"NOT_FOUND","message":"Not found."}""")

    override suspend fun expenses() = run("GET expenses") { expenses.toList() }

    override suspend fun createExpense(body: ExpenseBody) = run("POST expenses") {
        ExpenseDto(nextId++, body.title, body.amount, body.currency, body.category, body.tags, body.date).also { expenses += it }
    }

    override suspend fun updateExpense(id: Long, body: ExpenseBody) = run("PUT expenses/$id") {
        val index = expenses.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw notFound()
        ExpenseDto(id, body.title, body.amount, body.currency, body.category, body.tags, body.date).also { expenses[index] = it }
    }

    override suspend fun deleteExpense(id: Long) = run("DELETE expenses/$id") {
        if (!expenses.removeIf { it.id == id }) throw notFound()
    }

    override suspend fun subscriptions() = run("GET subscriptions") { subscriptions.toList() }

    override suspend fun createSubscription(body: SubscriptionBody) = run("POST subscriptions") {
        SubscriptionDto(nextId++, body.name, body.amount, body.currency, body.category, body.period, body.nextDate)
            .also { subscriptions += it; onSubscriptionSaved(it) }
    }

    override suspend fun updateSubscription(id: Long, body: SubscriptionBody) = run("PUT subscriptions/$id") {
        val index = subscriptions.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw notFound()
        SubscriptionDto(id, body.name, body.amount, body.currency, body.category, body.period, body.nextDate)
            .also { subscriptions[index] = it }
    }

    override suspend fun deleteSubscription(id: Long) = run("DELETE subscriptions/$id") {
        if (!subscriptions.removeIf { it.id == id }) throw notFound()
    }

    override suspend fun budgets() = run("GET budgets") { budgets.toList() }

    override suspend fun createBudget(body: BudgetBody) = run("POST budgets") {
        BudgetDto(nextId++, body.category, body.amount, body.currency).also { budgets += it }
    }

    override suspend fun updateBudget(id: Long, body: BudgetBody) = run("PUT budgets/$id") {
        val index = budgets.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw notFound()
        BudgetDto(id, body.category, body.amount, body.currency).also { budgets[index] = it }
    }

    override suspend fun deleteBudget(id: Long) = run("DELETE budgets/$id") {
        if (!budgets.removeIf { it.id == id }) throw notFound()
    }

    override suspend fun categories() = run("GET categories") { categories.toList() }

    override suspend fun createCategory(body: CategoryBody) = run("POST categories") {
        CategoryDto(nextId++, body.name, body.color, body.icon).also { categories += it }
    }

    override suspend fun updateCategory(id: Long, body: CategoryBody) = run("PUT categories/$id") {
        val index = categories.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw notFound()
        val old = categories[index].name
        CategoryDto(id, body.name, body.color, body.icon).also { categories[index] = it; onCategoryRenamed(old, body.name) }
    }

    override suspend fun deleteCategory(id: Long) = run("DELETE categories/$id") {
        if (!categories.removeIf { it.id == id }) throw notFound()
    }

    override suspend fun profile() = run("GET me") { ProfileDto(homeCurrency) }

    override suspend fun updateProfile(body: ProfileBody) = run("PUT me") {
        homeCurrency = body.homeCurrency
        expenses.replaceAll { it.copy(rate = rateOf(it.currency, homeCurrency)) }
        ProfileDto(homeCurrency)
    }

    override suspend fun rates() = run("GET rates") {
        if (ratesUnavailable) throw com.example.mybudget.data.auth.httpError(503)
        RatesDto(homeCurrency, rates)
    }

    /** Next id the fake server will give, to add rows with ids that don't clash. */
    fun takeId() = nextId++
}
