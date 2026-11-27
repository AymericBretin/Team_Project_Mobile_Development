package com.example.mybudget.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.mybudget.data.auth.User
import com.example.mybudget.ui.categories.CategoriesSection
import com.example.mybudget.ui.components.AppLocale
import com.example.mybudget.ui.components.DropdownField
import com.example.mybudget.ui.model.CustomCategory
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.mybudget.ui.model.SupportedCurrencies
import java.util.Currency

@Composable
fun ProfileScreen(
    user: User,
    homeCurrency: String,
    onHomeCurrencyChange: (String) -> Unit,
    categories: List<CustomCategory>,
    onCategoryClick: (CustomCategory) -> Unit,
    onAddCategory: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirm by remember { mutableStateOf(false) }
    val label = user.displayName?.takeIf { it.isNotBlank() } ?: user.email

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.first().uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
        if (label != user.email) Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DropdownField(
                label = "Main currency",
                value = homeCurrency,
                options = SupportedCurrencies,
                display = { currencyLabel(it) },
                onSelect = { it?.let(onHomeCurrencyChange) },
            )
            Text(
                "Totals and budgets are shown in this currency. Each expense keeps the exchange rate of the day it was made.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CategoriesSection(categories, onCategoryClick, onAddCategory, Modifier.padding(top = 16.dp))
        Box(Modifier.padding(top = 24.dp)) {
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Log out", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to see your expenses.") },
            confirmButton = { TextButton(onClick = { confirm = false; onLogout() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** "KRW · South Korean Won". */
private fun currencyLabel(code: String): String =
    runCatching { "$code · ${Currency.getInstance(code).getDisplayName(AppLocale)}" }.getOrDefault(code)
