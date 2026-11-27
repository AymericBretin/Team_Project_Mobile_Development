package com.example.mybudget.ui.model

import com.example.mybudget.data.auth.httpError
import com.example.mybudget.data.finance.DataError
import com.example.mybudget.data.finance.DefaultFinanceRepository
import com.example.mybudget.data.finance.FakeFinanceApi
import com.example.mybudget.data.finance.FinanceRepository.Companion.NEW_ID
import com.example.mybudget.data.finance.local.FinanceCache
import com.example.mybudget.data.finance.local.FinanceData
import com.example.mybudget.data.finance.remote.BudgetDto
import com.example.mybudget.data.finance.remote.ExpenseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val api = FakeFinanceApi()
    private val date = LocalDate.of(2026, 11, 24)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val cache = InMemoryFinanceCache()

    private fun viewModel() = BudgetViewModel(DefaultFinanceRepository(api), cache, userId = "1")

    @Test
    fun `loads the three lists from the server on start`() = runTest {
        api.expenses += ExpenseDto(1, "Coffee", 3.5, "EUR", date = "2026-11-24")
        api.budgets += BudgetDto(2, null, 1500.0, "EUR")

        val vm = viewModel()

        assertEquals(listOf(Expense(1, "Coffee", 3.5, date = date)), vm.expenses.value)
        assertEquals(listOf(Budget(2, null, 1500.0)), vm.budgets.value)
        assertEquals(emptyList<Subscription>(), vm.subscriptions.value)
        assertFalse(vm.isRefreshing.value)
        assertFalse(vm.isBusy.value)
        assertNull(vm.message.value)
    }

    @Test
    fun `a new expense appears with its server id, an edited one is replaced`() = runTest {
        val vm = viewModel()

        vm.saveExpense(Expense(NEW_ID, "Coffee", 3.5, date = date))
        val created = vm.expenses.value.single()
        vm.saveExpense(created.copy(amount = 4.0))

        assertEquals(listOf(Expense(created.id, "Coffee", 4.0, date = date)), vm.expenses.value)
        assertEquals(1, api.expenses.size)
    }

    @Test
    fun `delete removes the item`() = runTest {
        api.budgets += BudgetDto(2, null, 1500.0, "EUR")
        val vm = viewModel()

        vm.deleteBudget(2)

        assertEquals(emptyList<Budget>(), vm.budgets.value)
        assertEquals(emptyList<BudgetDto>(), api.budgets)
    }

    @Test
    fun `a failed save keeps the list and reports the error`() = runTest {
        val vm = viewModel()
        api.failWith = IOException("offline")

        vm.saveExpense(Expense(NEW_ID, "Coffee", 3.5, date = date))

        assertEquals(emptyList<Expense>(), vm.expenses.value)
        assertEquals(UserMessage.Failure(DataError.Network, isLoadError = false), vm.message.value)
        vm.messageShown()
        assertNull(vm.message.value)
    }

    @Test
    fun `a failed load is reported once, as retryable, and retry reloads`() = runTest {
        api.failWith = httpError(500)
        val vm = viewModel()

        assertEquals(UserMessage.Failure(DataError.Server, isLoadError = true), vm.message.value)
        assertEquals(6, api.calls.size)

        api.failWith = null
        api.expenses += ExpenseDto(1, "Coffee", 3.5, "EUR", date = "2026-11-24")
        vm.refresh()

        assertEquals(1, vm.expenses.value.size)
    }

    @Test
    fun `an expired session shows no message (the app goes back to login)`() = runTest {
        api.failWith = httpError(401)

        assertNull(viewModel().message.value)
    }

    @Test
    fun `loads the home currency and today's rates`() = runTest {
        api.homeCurrency = "KRW"
        api.rates = mapOf("KRW" to 1.0, "EUR" to 1500.0)

        assertEquals(CurrencyConverter("KRW", api.rates), viewModel().converter.value)
    }

    @Test
    fun `without rates the app keeps working, in the right home currency, without an error`() = runTest {
        api.homeCurrency = "KRW"
        api.ratesUnavailable = true

        val vm = viewModel()

        assertEquals(CurrencyConverter("KRW"), vm.converter.value)
        assertNull(vm.message.value)
    }

    @Test
    fun `changing the home currency reloads everything converted by the server`() = runTest {
        api.expenses += ExpenseDto(1, "Ramen", 9000.0, "KRW", date = "2026-11-24", rate = 0.00068)
        api.rateOf = { currency, home -> if (currency == home) 1.0 else null }
        val vm = viewModel()

        api.rates = mapOf("KRW" to 1.0, "EUR" to 1470.0)
        vm.setHomeCurrency("KRW")

        assertEquals("KRW", vm.converter.value.home)
        assertEquals(1.0, vm.expenses.value.single().rate!!, 0.0)
        assertFalse(vm.isRefreshing.value)
        assertEquals("KRW", cache.saved["1"]?.converter?.home)
    }

    @Test
    fun `offline, the last loaded data is shown with a retryable error`() = runTest {
        api.expenses += ExpenseDto(1, "Coffee", 3.5, "EUR", date = "2026-11-24")
        viewModel()
        api.failWith = IOException("offline")

        val vm = viewModel()

        assertEquals(listOf(Expense(1, "Coffee", 3.5, date = date)), vm.expenses.value)
        assertEquals(UserMessage.Failure(DataError.Network, isLoadError = true), vm.message.value)
    }

    @Test
    fun `saving a due subscription shows the expense the server charged for it`() = runTest {
        api.onSubscriptionSaved = { sub ->
            api.expenses += ExpenseDto(api.takeId(), sub.name, sub.amount, sub.currency, date = sub.nextDate, subscriptionId = sub.id)
        }
        val vm = viewModel()

        vm.saveSubscription(Subscription(NEW_ID, "Gym", 30.0, nextDate = date))

        assertEquals(listOf("Gym"), vm.expenses.value.map { it.title })
        assertEquals(vm.subscriptions.value.single().id, vm.expenses.value.single().subscriptionId)
    }

    @Test
    fun `an expense that pushes a budget past 80 percent then past 100 percent warns each time`() = runTest {
        api.budgets += BudgetDto(1, "Restaurant", 100.0, "EUR")
        api.budgets += BudgetDto(2, null, 1000.0, "EUR")
        val vm = viewModel()

        vm.saveExpense(Expense(NEW_ID, "Dinner", 85.0, category = "Restaurant", date = date))
        val warning = (vm.message.value as UserMessage.BudgetWarning).alert
        assertEquals("Restaurant", warning.usage.budget.category)
        assertEquals(0.85, warning.usage.ratio, 1e-9)
        assertEquals(YearMonth.of(2026, 11), warning.month)
        vm.messageShown()

        vm.saveExpense(Expense(NEW_ID, "Snack", 5.0, category = "Restaurant", date = date))
        assertNull("still between 80 and 100 %: no new warning", vm.message.value)

        vm.saveExpense(Expense(NEW_ID, "Drinks", 20.0, category = "Restaurant", date = date))
        assertTrue((vm.message.value as UserMessage.BudgetWarning).alert.usage.isOver)
    }

    @Test
    fun `renaming a category reloads what the server renamed with it`() = runTest {
        api.expenses += ExpenseDto(1, "Kibble", 20.0, "EUR", category = "Pets", date = "2026-11-24")
        val vm = viewModel()
        vm.saveCategory(CustomCategory(NEW_ID, "Pets", "amber", "pets"))
        val pets = vm.categories.value.single()

        api.onCategoryRenamed = { old, new -> api.expenses.replaceAll { if (it.category == old) it.copy(category = new) else it } }
        vm.saveCategory(pets.copy(name = "Animals"))

        assertEquals(listOf("Animals"), vm.categories.value.map { it.name })
        assertEquals("Animals", vm.expenses.value.single().category)
        assertEquals(listOf("Animals"), cache.saved["1"]?.categories?.map { it.name })
    }
}

/** Keeps what the ViewModel saves, as the file cache would across app starts. */
private class InMemoryFinanceCache : FinanceCache {
    val saved = mutableMapOf<String, FinanceData>()
    override suspend fun load(userId: String) = saved[userId]
    override suspend fun save(userId: String, data: FinanceData) { saved[userId] = data }
    override suspend fun clear() = saved.clear()
}
