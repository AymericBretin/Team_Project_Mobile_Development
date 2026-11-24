package com.example.mybudget.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mybudget.data.auth.AuthState
import com.example.mybudget.data.auth.User
import com.example.mybudget.ui.auth.AuthScreen
import com.example.mybudget.ui.auth.AuthViewModel
import com.example.mybudget.ui.auth.SplashScreen
import com.example.mybudget.ui.budgets.BudgetEditorSheet
import com.example.mybudget.ui.budgets.BudgetsScreen
import com.example.mybudget.ui.expenses.ExpenseEditorSheet
import com.example.mybudget.ui.expenses.ExpensesScreen
import com.example.mybudget.ui.home.DashboardScreen
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.BudgetViewModel
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Subscription
import com.example.mybudget.ui.profile.ProfileScreen
import com.example.mybudget.ui.subscriptions.SubscriptionEditorSheet
import com.example.mybudget.ui.subscriptions.SubscriptionsScreen

private enum class Tab(val title: String, val icon: ImageVector, val hasAddButton: Boolean) {
    Home("Home", Icons.Filled.Home, true),
    Expenses("Expenses", Icons.Filled.Receipt, true),
    Bills("Bills", Icons.Filled.Autorenew, true),
    Budgets("Budgets", Icons.Filled.PieChart, true),
    Profile("Profile", Icons.Filled.Person, false),
}

/** What the editor bottom sheet is currently showing (null = closed). `item == null` means "create". */
private sealed interface Editor {
    data class ForExpense(val item: Expense?) : Editor
    data class ForSubscription(val item: Subscription?) : Editor
    data class ForBudget(val item: Budget?) : Editor
}

/** Top of the UI: picks the screen from [AuthViewModel.authState], as described in docs/auth.md. */
@Composable
fun AppRoot(authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Loading -> SplashScreen()
        AuthState.Unauthenticated -> AuthScreen(
            isLoading = uiState.isLoading,
            error = uiState.error,
            onLogin = authViewModel::login,
            onRegister = authViewModel::register,
            onFieldChange = authViewModel::clearError,
        )
        is AuthState.Authenticated -> MainShell(user = state.user, onLogout = authViewModel::logout)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(user: User, onLogout: () -> Unit) {
    // Keyed by user so that logging in as someone else starts from a fresh ViewModel, not the previous user's data.
    val vm: BudgetViewModel = viewModel(key = "budget-${user.id}")
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var editor by remember { mutableStateOf<Editor?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.Home) "My Budget" else tab.title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab.hasAddButton) {
                FloatingActionButton(
                    onClick = {
                        editor = when (tab) {
                            Tab.Bills -> Editor.ForSubscription(null)
                            Tab.Budgets -> Editor.ForBudget(null)
                            else -> Editor.ForExpense(null)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            Tab.Home -> DashboardScreen(
                greetingName = user.displayName,
                expenses = expenses,
                subscriptions = subscriptions,
                budgets = budgets,
                onSeeAllExpenses = { tab = Tab.Expenses },
                onSeeSubscriptions = { tab = Tab.Bills },
                onExpenseClick = { editor = Editor.ForExpense(it) },
                modifier = content,
            )
            Tab.Expenses -> ExpensesScreen(expenses, onExpenseClick = { editor = Editor.ForExpense(it) }, modifier = content)
            Tab.Bills -> SubscriptionsScreen(subscriptions, onClick = { editor = Editor.ForSubscription(it) }, modifier = content)
            Tab.Budgets -> BudgetsScreen(budgets, expenses, onClick = { editor = Editor.ForBudget(it) }, modifier = content)
            Tab.Profile -> ProfileScreen(user, onLogout, modifier = content)
        }
    }

    when (val e = editor) {
        null -> Unit
        is Editor.ForExpense -> ExpenseEditorSheet(e.item, vm::saveExpense, { vm.deleteExpense(it.id) }, { editor = null })
        is Editor.ForSubscription -> SubscriptionEditorSheet(e.item, vm::saveSubscription, { vm.deleteSubscription(it.id) }, { editor = null })
        is Editor.ForBudget -> BudgetEditorSheet(e.item, budgets, vm::saveBudget, { vm.deleteBudget(it.id) }, { editor = null })
    }
}
