package com.example.mybudget

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mybudget.ui.AppRoot
import com.example.mybudget.ui.components.AppLocale
import com.example.mybudget.ui.theme.MyBudgetTheme

class MainActivity : ComponentActivity() {

    /** English UI whatever the phone's language, Material components included (see [AppLocale]). */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(AppLocale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyBudgetTheme {
                AppRoot()
            }
        }
    }
}
