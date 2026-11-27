package com.example.mybudget.data.finance.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Expenses, subscriptions, budgets and currency settings of the signed-in user.
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

    @GET("categories")
    suspend fun categories(): List<CategoryDto>

    @POST("categories")
    suspend fun createCategory(@Body body: CategoryBody): CategoryDto

    /** Renaming also renames the category on every expense, subscription and budget using it. */
    @PUT("categories/{id}")
    suspend fun updateCategory(@Path("id") id: Long, @Body body: CategoryBody): CategoryDto

    /** Its expenses and subscriptions become uncategorised, its budget is deleted. */
    @DELETE("categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Long)

    @GET("me")
    suspend fun profile(): ProfileDto

    /** Changing the home currency makes the server convert every expense again, at the rate of its own date. */
    @PUT("me")
    suspend fun updateProfile(@Body body: ProfileBody): ProfileDto

    @GET("rates")
    suspend fun rates(): RatesDto
}
