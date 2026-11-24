package com.example.mybudget.data.finance.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Expenses, subscriptions and budgets of the signed-in user.
 * Must be created from `authenticatedRetrofit`: the server answers 401 without a valid access token.
 */
interface FinanceApi {
    @GET("expenses")
    suspend fun expenses(): List<ExpenseDto>

    @POST("expenses")
    suspend fun createExpense(@Body body: ExpenseBody): ExpenseDto

    @PUT("expenses/{id}")
    suspend fun updateExpense(@Path("id") id: Long, @Body body: ExpenseBody): ExpenseDto

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: Long)

    @GET("subscriptions")
    suspend fun subscriptions(): List<SubscriptionDto>

    @POST("subscriptions")
    suspend fun createSubscription(@Body body: SubscriptionBody): SubscriptionDto

    @PUT("subscriptions/{id}")
    suspend fun updateSubscription(@Path("id") id: Long, @Body body: SubscriptionBody): SubscriptionDto

    @DELETE("subscriptions/{id}")
    suspend fun deleteSubscription(@Path("id") id: Long)

    @GET("budgets")
    suspend fun budgets(): List<BudgetDto>

    @POST("budgets")
    suspend fun createBudget(@Body body: BudgetBody): BudgetDto

    @PUT("budgets/{id}")
    suspend fun updateBudget(@Path("id") id: Long, @Body body: BudgetBody): BudgetDto

    @DELETE("budgets/{id}")
    suspend fun deleteBudget(@Path("id") id: Long)
}
