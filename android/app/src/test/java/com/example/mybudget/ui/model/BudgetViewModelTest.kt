package com.example.mybudget.ui.model

import com.example.mybudget.data.auth.httpError
import com.example.mybudget.data.finance.DataError
import com.example.mybudget.data.finance.DefaultFinanceRepository
import com.example.mybudget.data.finance.FakeFinanceApi
import com.example.mybudget.data.finance.FinanceRepository.Companion.NEW_ID
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
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val api = FakeFinanceApi()
    private val date = LocalDate.of(2026, 11, 24)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BudgetViewModel(DefaultFinanceRepository(api))

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
        assertEquals(UserMessage(DataError.Network, isLoadError = false), vm.message.value)
        vm.messageShown()
        assertNull(vm.message.value)
    }

    @Test
    fun `a failed load is reported once, as retryable, and retry reloads`() = runTest {
        api.failWith = httpError(500)
        val vm = viewModel()

        assertEquals(UserMessage(DataError.Server, isLoadError = true), vm.message.value)
        assertEquals(3, api.calls.size)

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
}
