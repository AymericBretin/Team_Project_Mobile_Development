package com.example.mybudget.data.finance

import com.example.mybudget.data.auth.remote.ErrorResponse
import com.example.mybudget.data.finance.FinanceRepository.Companion.NEW_ID
import com.example.mybudget.data.finance.remote.FinanceApi
import com.example.mybudget.data.finance.remote.ProfileBody
import com.example.mybudget.data.finance.remote.toBody
import com.example.mybudget.data.finance.remote.toModel
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

class DefaultFinanceRepository(
    private val api: FinanceApi,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FinanceRepository {

    override suspend fun expenses() = call { api.expenses().map { it.toModel() } }

    override suspend fun saveExpense(expense: Expense) = call {
        val body = expense.toBody()
        if (expense.id == NEW_ID) api.createExpense(body).toModel() else api.updateExpense(expense.id, body).toModel()
    }

    override suspend fun deleteExpense(id: Long) = delete { api.deleteExpense(id) }

    override suspend fun subscriptions() = call { api.subscriptions().map { it.toModel() } }

    override suspend fun saveSubscription(subscription: Subscription) = call {
        val body = subscription.toBody()
        if (subscription.id == NEW_ID) api.createSubscription(body).toModel()
        else api.updateSubscription(subscription.id, body).toModel()
    }

    override suspend fun deleteSubscription(id: Long) = delete { api.deleteSubscription(id) }

    override suspend fun budgets() = call { api.budgets().map { it.toModel() } }

    override suspend fun saveBudget(budget: Budget) = call {
        val body = budget.toBody()
        if (budget.id == NEW_ID) api.createBudget(body).toModel() else api.updateBudget(budget.id, body).toModel()
    }

    override suspend fun deleteBudget(id: Long) = delete { api.deleteBudget(id) }

    override suspend fun categories() = call { api.categories().map { it.toModel() } }

    override suspend fun saveCategory(category: CustomCategory) = call {
        val body = category.toBody()
        if (category.id == NEW_ID) api.createCategory(body).toModel() else api.updateCategory(category.id, body).toModel()
    }

    override suspend fun deleteCategory(id: Long) = delete { api.deleteCategory(id) }

    override suspend fun homeCurrency() = call { api.profile().homeCurrency }

    override suspend fun setHomeCurrency(currency: String) = call { api.updateProfile(ProfileBody(currency)).homeCurrency }

    override suspend fun rates() = call { api.rates().toModel() }

    /** Deleting something that is already gone is a success: the user gets what they asked for. */
    private suspend fun delete(block: suspend () -> Unit): DataResult<Unit> =
        when (val result = call(block)) {
            DataResult.Failure(DataError.NotFound) -> DataResult.Success(Unit)
            else -> result
        }

    private suspend fun <T> call(block: suspend () -> T): DataResult<T> = try {
        DataResult.Success(block())
    } catch (e: HttpException) {
        DataResult.Failure(e.toDataError())
    } catch (_: IOException) {
        DataResult.Failure(DataError.Network)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Unexpected response (e.g. malformed JSON or date).
        DataResult.Failure(DataError.Unknown(e.message))
    }

    private fun HttpException.toDataError(): DataError = when (code()) {
        400, 422 -> DataError.Validation(parseError()?.message)
        401 -> DataError.SessionExpired
        404 -> DataError.NotFound
        in 500..599 -> DataError.Server
        else -> DataError.Unknown(parseError()?.message ?: message())
    }

    private fun HttpException.parseError(): ErrorResponse? = try {
        response()?.errorBody()?.string()?.let { json.decodeFromString(ErrorResponse.serializer(), it) }
    } catch (_: Exception) {
        null
    }
}
