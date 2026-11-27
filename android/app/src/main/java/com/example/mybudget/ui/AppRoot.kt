package com.example.mybudget.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mybudget.data.auth.AuthState
import com.example.mybudget.data.auth.User
import com.example.mybudget.data.finance.DataError
import com.example.mybudget.ui.auth.AuthScreen
import com.example.mybudget.ui.auth.AuthViewModel
import com.example.mybudget.ui.auth.SplashScreen
import com.example.mybudget.ui.budgets.BudgetEditorSheet
import com.example.mybudget.ui.budgets.BudgetsScreen
import com.example.mybudget.ui.expenses.ExpenseEditorSheet
import com.example.mybudget.ui.expenses.ExpensesScreen
import com.example.mybudget.ui.home.DashboardScreen
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.BudgetAlert
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.UserMessage
import com.example.mybudget.ui.categories.CategoryEditorSheet
import com.example.mybudget.ui.components.AppLocale
import com.example.mybudget.ui.components.CategoryStyles
import com.example.mybudget.ui.components.LocalCategoryStyles
import androidx.compose.runtime.CompositionLocalProvider
import java.time.YearMonth
import java.time.format.TextStyle
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
    data class ForCategory(val item: CustomCategory?) : Editor
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
    val vm: BudgetViewModel = viewModel(key = "budget-${user.id}", factory = BudgetViewModel.factory(user.id))
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val converter by vm.converter.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val categoryStyles = remember(categories) { CategoryStyles(categories) }
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        when (val shown = message ?: return@LaunchedEffect) {
            is UserMessage.Failure -> {
                val result = snackbar.showSnackbar(
                    message = shown.error.message(),
                    actionLabel = if (shown.isLoadError) "Retry" else null,
                    duration = if (shown.isLoadError) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                vm.messageShown()
                if (result == SnackbarResult.ActionPerformed) vm.refresh()
            }
            is UserMessage.BudgetWarning -> {
                val result = snackbar.showSnackbar(shown.alert.message(), actionLabel = "Budgets", duration = SnackbarDuration.Long)
                vm.messageShown()
                if (result == SnackbarResult.ActionPerformed) tab = Tab.Budgets
            }
        }
    }

    CompositionLocalProvider(LocalCategoryStyles provides categoryStyles) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(if (tab == Tab.Home) "My Budget" else tab.title) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    // Saves and deletes in progress. Space is always reserved so the content doesn't jump.
                    Box(Modifier.fillMaxWidth().height(2.dp)) {
                        if (isBusy && !isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
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
            val content = Modifier.fillMaxSize()
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding)) {
                when (tab) {
                    Tab.Home -> DashboardScreen(
                        greetingName = user.displayName,
                        expenses = expenses,
                        subscriptions = subscriptions,
                        budgets = budgets,
                        converter = converter,
                        onSeeAllExpenses = { tab = Tab.Expenses },
                        onSeeSubscriptions = { tab = Tab.Bills },
                        onSeeBudgets = { tab = Tab.Budgets },
                        onExpenseClick = { editor = Editor.ForExpense(it) },
                        modifier = content,
                    )
                    Tab.Expenses -> ExpensesScreen(expenses, converter, onExpenseClick = { editor = Editor.ForExpense(it) }, modifier = content)
                    Tab.Bills -> SubscriptionsScreen(subscriptions, converter, onClick = { editor = Editor.ForSubscription(it) }, modifier = content)
                    Tab.Budgets -> BudgetsScreen(budgets, expenses, converter, onClick = { editor = Editor.ForBudget(it) }, modifier = content)
                    Tab.Profile -> ProfileScreen(
                        user = user,
                        homeCurrency = converter.home,
                        onHomeCurrencyChange = vm::setHomeCurrency,
                        categories = categories,
                        onCategoryClick = { editor = Editor.ForCategory(it) },
                        onAddCategory = { editor = Editor.ForCategory(null) },
                        onLogout = onLogout,
                        modifier = content,
                    )
                }
            }
        }

        when (val e = editor) {
            null -> Unit
            is Editor.ForExpense -> {
                // Abroad, the latest expense is usually in the local currency: start new ones in it.
                val latestCurrency = expenses.maxWithOrNull(compareBy<Expense>({ it.date }, { it.id }))?.currency
                ExpenseEditorSheet(e.item, latestCurrency ?: converter.home, vm::saveExpense, { vm.deleteExpense(it.id) }, { editor = null })
            }
            is Editor.ForSubscription ->
                SubscriptionEditorSheet(e.item, converter.home, vm::saveSubscription, { vm.deleteSubscription(it.id) }, { editor = null })
            is Editor.ForBudget -> BudgetEditorSheet(e.item, budgets, converter.home, vm::saveBudget, { vm.deleteBudget(it.id) }, { editor = null })
            is Editor.ForCategory ->
                CategoryEditorSheet(e.item, categories, vm::saveCategory, { vm.deleteCategory(it.id) }, { editor = null })
        }
    }
}

/** "Restaurant budget: 85% used" / "Over your monthly budget for October (112%)". */
private fun BudgetAlert.message(): String {
    val budget = usage.budget.category?.let { "$it budget" } ?: "monthly budget"
    val percent = "${(usage.ratio * 100).toInt()}%"
    val period = if (month == YearMonth.now()) "" else " for ${month.month.getDisplayName(TextStyle.FULL, AppLocale)}"
    return if (usage.isOver) "Over your $budget$period ($percent)"
    else "${budget.replaceFirstChar { it.uppercase() }}$period: $percent used"
}

private fun DataError.message(): String = when (this) {
    is DataError.Validation -> message ?: "Please check your details"
    DataError.NotFound -> "This item no longer exists"
    DataError.SessionExpired -> "Your session has expired, please log in again"
    DataError.Network -> "No internet connection"
    DataError.Server, is DataError.Unknown -> "Something went wrong, please try again"
}
