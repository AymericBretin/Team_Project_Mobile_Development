package com.example.mybudget.data.finance

import com.example.mybudget.data.finance.remote.BudgetBody
import com.example.mybudget.data.finance.remote.BudgetDto
import com.example.mybudget.data.finance.remote.ExpenseBody
import com.example.mybudget.data.finance.remote.ExpenseDto
import com.example.mybudget.data.finance.remote.FinanceApi
import com.example.mybudget.data.finance.remote.SubscriptionBody
import com.example.mybudget.data.finance.remote.SubscriptionDto

/** In-memory server: behaves like the CRUD routes of server/src/index.ts. [failWith] makes every call throw. */
class FakeFinanceApi : FinanceApi {
    val expenses = mutableListOf<ExpenseDto>()
    val subscriptions = mutableListOf<SubscriptionDto>()
    val budgets = mutableListOf<BudgetDto>()
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
            .also { subscriptions += it }
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
}
