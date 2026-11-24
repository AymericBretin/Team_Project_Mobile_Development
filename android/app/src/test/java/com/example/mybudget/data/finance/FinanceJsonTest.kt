package com.example.mybudget.data.finance

import com.example.mybudget.data.finance.remote.BudgetDto
import com.example.mybudget.data.finance.remote.ExpenseDto
import com.example.mybudget.data.finance.remote.SubscriptionDto
import com.example.mybudget.data.finance.remote.toBody
import com.example.mybudget.data.network.HttpClients
import com.example.mybudget.ui.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Checks the JSON exchanged with the server (server/src/index.ts) with the JSON config the app really uses. */
class FinanceJsonTest {

    private val json = HttpClients.apiJson

    @Test
    fun `request bodies send every field, nulls included, so a PUT can clear a category`() {
        val body = Expense(5, "Coffee", 3.5, "EUR", category = null, tags = listOf("work"), date = LocalDate.of(2026, 11, 24)).toBody()

        assertEquals(
            """{"title":"Coffee","amount":3.5,"currency":"EUR","category":null,"tags":["work"],"date":"2026-11-24"}""",
            json.encodeToString(body),
        )
    }

    @Test
    fun `decodes the rows returned by the server`() {
        val expense = json.decodeFromString<ExpenseDto>(
            """{"id":1,"title":"Coffee","amount":3.5,"currency":"EUR","category":null,"tags":[],"date":"2026-11-24"}""",
        )
        val subscription = json.decodeFromString<SubscriptionDto>(
            """{"id":2,"name":"Netflix","amount":13.99,"currency":"EUR","category":"Subscriptions","period":"monthly","nextDate":"2026-12-01"}""",
        )
        val budget = json.decodeFromString<BudgetDto>("""{"id":3,"category":null,"amount":1500,"currency":"EUR"}""")

        assertEquals(ExpenseDto(1, "Coffee", 3.5, "EUR", null, emptyList(), "2026-11-24"), expense)
        assertEquals("2026-12-01", subscription.nextDate)
        assertEquals(BudgetDto(3, null, 1500.0, "EUR"), budget)
    }

    @Test
    fun `ignores fields the server may add later`() {
        val budget = json.decodeFromString<BudgetDto>("""{"id":3,"category":"Travel","amount":200,"currency":"EUR","period":"monthly"}""")

        assertEquals("Travel", budget.category)
    }
}
