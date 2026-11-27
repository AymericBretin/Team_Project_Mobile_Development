package com.example.mybudget.data.finance

import com.example.mybudget.data.auth.httpError
import com.example.mybudget.data.finance.FinanceRepository.Companion.NEW_ID
import com.example.mybudget.data.finance.remote.ExpenseDto
import com.example.mybudget.data.finance.remote.SubscriptionDto
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.CurrencyConverter
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Period
import com.example.mybudget.ui.model.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class DefaultFinanceRepositoryTest {

    private val api = FakeFinanceApi()
    private val repository = DefaultFinanceRepository(api)

    private val date = LocalDate.of(2026, 11, 24)

    @Test
    fun `maps server expenses to UI models`() = runTest {
        api.expenses += ExpenseDto(7, "Ramen", 9000.0, "KRW", "Restaurant", listOf("seoul"), "2026-11-24")

        assertEquals(
            DataResult.Success(listOf(Expense(7, "Ramen", 9000.0, "KRW", "Restaurant", listOf("seoul"), date))),
            repository.expenses(),
        )
    }

    @Test
    fun `maps subscription periods, unknown ones default to monthly`() = runTest {
        api.subscriptions += SubscriptionDto(1, "Gym", 30.0, "EUR", null, "yearly", "2026-12-01")
        api.subscriptions += SubscriptionDto(2, "Odd", 5.0, "EUR", null, "daily", "2026-12-01")

        val subs = (repository.subscriptions() as DataResult.Success).data

        assertEquals(listOf(Period.YEARLY, Period.MONTHLY), subs.map { it.period })
        assertEquals(LocalDate.of(2026, 12, 1), subs.first().nextDate)
    }

    @Test
    fun `saving a new item creates it and returns it with its server id`() = runTest {
        val result = repository.saveExpense(Expense(NEW_ID, "Coffee", 3.5, date = date))

        assertEquals(listOf("POST expenses"), api.calls)
        assertEquals(1L, (result as DataResult.Success).data.id)
    }

    @Test
    fun `saving an existing item updates it`() = runTest {
        val created = (repository.saveBudget(Budget(NEW_ID, "Groceries", 300.0)) as DataResult.Success).data

        val updated = repository.saveBudget(created.copy(category = null, amount = 1500.0))

        assertEquals(listOf("POST budgets", "PUT budgets/${created.id}"), api.calls)
        assertEquals(DataResult.Success(Budget(created.id, null, 1500.0)), updated)
    }

    @Test
    fun `subscriptions are sent with the period value the server expects`() = runTest {
        repository.saveSubscription(Subscription(NEW_ID, "Gym", 30.0, period = Period.WEEKLY, nextDate = date))

        assertEquals("weekly", api.subscriptions.single().period)
        assertEquals("2026-11-24", api.subscriptions.single().nextDate)
    }

    @Test
    fun `expenses keep the rate of their day and the subscription that created them`() = runTest {
        api.expenses += ExpenseDto(7, "Gym", 9000.0, "KRW", "Health", emptyList(), "2026-11-24", rate = 0.00068, subscriptionId = 3)

        val expense = (repository.expenses() as DataResult.Success).data.single()

        assertEquals(0.00068, expense.rate!!, 1e-12)
        assertEquals(3L, expense.subscriptionId)
    }

    @Test
    fun `reads and changes the home currency, rates are relative to it`() = runTest {
        api.rates = mapOf("EUR" to 1.0, "KRW" to 0.00068)

        assertEquals(DataResult.Success("EUR"), repository.homeCurrency())
        assertEquals(DataResult.Success(CurrencyConverter("EUR", api.rates)), repository.rates())
        assertEquals(DataResult.Success("KRW"), repository.setHomeCurrency("KRW"))
        assertEquals(listOf("GET me", "GET rates", "PUT me"), api.calls)
    }

    @Test
    fun `custom categories are created, then updated`() = runTest {
        val created = (repository.saveCategory(CustomCategory(NEW_ID, "Pets", "amber", "pets")) as DataResult.Success).data
        repository.saveCategory(created.copy(name = "Animals"))

        assertEquals(listOf("POST categories", "PUT categories/${created.id}"), api.calls)
        assertEquals(DataResult.Success(listOf(CustomCategory(created.id, "Animals", "amber", "pets"))), repository.categories())
    }

    @Test
    fun `deleting an item that is already gone is a success`() = runTest {
        assertEquals(DataResult.Success(Unit), repository.deleteExpense(42))
    }

    @Test
    fun `updating an item that is gone fails with NotFound`() = runTest {
        assertEquals(DataResult.Failure(DataError.NotFound), repository.saveExpense(Expense(42, "x", 1.0)))
    }

    @Test
    fun `maps HTTP and network errors`() = runTest {
        api.failWith = httpError(400, """{"code":"VALIDATION_ERROR","message":"Amount must be positive."}""")
        assertEquals(DataResult.Failure(DataError.Validation("Amount must be positive.")), repository.budgets())

        api.failWith = httpError(401)
        assertEquals(DataResult.Failure(DataError.SessionExpired), repository.budgets())

        api.failWith = httpError(500)
        assertEquals(DataResult.Failure(DataError.Server), repository.budgets())

        api.failWith = IOException("offline")
        assertEquals(DataResult.Failure(DataError.Network), repository.budgets())
    }

    @Test
    fun `a malformed server response does not crash`() = runTest {
        api.expenses += ExpenseDto(1, "x", 1.0, "EUR", date = "not-a-date")

        assertEquals(DataError.Unknown::class, ((repository.expenses() as DataResult.Failure).error)::class)
    }
}
