package com.example.mybudget

import android.app.Application
import com.example.mybudget.data.auth.AuthState
import com.example.mybudget.di.AppContainer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class MyBudgetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Signed out or session expired: the user's data must not stay on the device.
        MainScope().launch {
            container.authRepository.authState
                .filter { it is AuthState.Unauthenticated }
                .collect { container.financeCache.clear() }
        }
    }
}
