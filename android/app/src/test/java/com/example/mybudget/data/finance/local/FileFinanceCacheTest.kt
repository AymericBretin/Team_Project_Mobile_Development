package com.example.mybudget.data.finance.local

import com.example.mybudget.data.network.HttpClients
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.CurrencyConverter
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Period
import com.example.mybudget.ui.model.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class FileFinanceCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val cache by lazy { FileFinanceCache(folder.root, HttpClients.json) }

    private val data = FinanceData(
        converter = CurrencyConverter("KRW", mapOf("EUR" to 1470.0, "KRW" to 1.0)),
        expenses = listOf(Expense(1, "Ramen", 9000.0, "KRW", "Restaurant", listOf("seoul"), LocalDate.of(2026, 11, 24), 1.0, 7)),
        subscriptions = listOf(Subscription(7, "Gym", 30.0, "EUR", "Health", Period.WEEKLY, LocalDate.of(2026, 12, 1))),
        budgets = listOf(Budget(3, null, 1_500_000.0, "KRW")),
        categories = listOf(CustomCategory(4, "Pets", "amber", "pets")),
    )

    @Test
    fun `keeps the data of each user across app starts`() = runTest {
        cache.save("1", data)

        assertEquals(data, FileFinanceCache(folder.root, HttpClients.json).load("1"))
        assertNull(cache.load("2"))
    }

    @Test
    fun `clear removes every user's data`() = runTest {
        cache.save("1", data)
        cache.save("2", data)

        cache.clear()

        assertNull(cache.load("1"))
        assertNull(cache.load("2"))
    }

    @Test
    fun `an unreadable file is ignored`() = runTest {
        File(folder.root, "finance-1.json").writeText("{not json")

        assertNull(cache.load("1"))
    }

    @Test
    fun `a cache written before custom categories existed still loads`() = runTest {
        File(folder.root, "finance-1.json").writeText(
            """{"homeCurrency":"EUR","rates":{},"expenses":[],"subscriptions":[],"budgets":[]}""",
        )

        assertEquals(emptyList<CustomCategory>(), cache.load("1")?.categories)
    }
}
