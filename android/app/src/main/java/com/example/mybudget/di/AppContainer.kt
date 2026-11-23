package com.example.mybudget.di

import android.content.Context
import com.example.mybudget.data.auth.AuthRepository
import com.example.mybudget.data.auth.DefaultAuthRepository
import com.example.mybudget.data.auth.local.EncryptedSessionStorage
import com.example.mybudget.data.auth.remote.AuthApi
import com.example.mybudget.data.network.HttpClients
import retrofit2.Retrofit
import retrofit2.create

/**
 * Manual dependency injection: a single instance per app, created in [com.example.mybudget.MyBudgetApplication].
 * Can be swapped for Hilt later without touching the classes, which get everything through their constructor.
 */
class AppContainer(context: Context) {

    private val baseClient = HttpClients.base()

    private val defaultAuthRepository = DefaultAuthRepository(
        api = HttpClients.retrofit(baseClient).create<AuthApi>(),
        storage = EncryptedSessionStorage(context),
        json = HttpClients.json,
    )

    val authRepository: AuthRepository = defaultAuthRepository

    /**
     * Retrofit instance for every business API (budgets, transactions…):
     * the token is attached and refreshed automatically.
     *
     * Example: `val budgetApi = container.authenticatedRetrofit.create<BudgetApi>()`
     */
    val authenticatedRetrofit: Retrofit = HttpClients.retrofit(
        HttpClients.authenticated(baseClient, defaultAuthRepository),
    )
}
