package com.example.mybudget

import android.app.Application
import com.example.mybudget.di.AppContainer

class MyBudgetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
