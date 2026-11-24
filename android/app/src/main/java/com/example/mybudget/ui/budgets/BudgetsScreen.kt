package com.example.mybudget.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mybudget.ui.components.AmountField
import com.example.mybudget.ui.components.BudgetBar
import com.example.mybudget.ui.components.CategoryBadge
import com.example.mybudget.ui.components.DropdownField
import com.example.mybudget.ui.components.EmptyState
import com.example.mybudget.ui.components.formatMoney
import com.example.mybudget.ui.components.toAmountOrNull
import com.example.mybudget.ui.components.toAmountText
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.DefaultCategories
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.SupportedCurrencies
import com.example.mybudget.ui.model.amountEur
import com.example.mybudget.ui.model.inMonth
import com.example.mybudget.ui.model.totalEur
import java.time.YearMonth

@Composable
fun BudgetsScreen(
    budgets: List<Budget>,
    expenses: List<Expense>,
    onClick: (Budget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthExpenses = expenses.inMonth(YearMonth.now())
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (budgets.isEmpty()) item { EmptyState("No budgets yet", "Set a monthly limit overall or per category.") }
        items(budgets.sortedBy { it.category != null }, key = { it.id }) { budget ->
            val spent = if (budget.category == null) monthExpenses.totalEur()
            else monthExpenses.filter { it.category == budget.category }.totalEur()
            val progress = (spent / budget.amountEur()).toFloat()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { onClick(budget) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryBadge(budget.category)
                        Text(budget.category ?: "Monthly budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BudgetBar(progress)
                    Text(
                        "${formatMoney(spent, "EUR")} of ${formatMoney(budget.amount, budget.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorSheet(
    initial: Budget?,
    existing: List<Budget>,
    onSave: (Budget) -> Unit,
    onDelete: ((Budget) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf(initial?.category) }
    var amount by remember { mutableStateOf(initial?.amount?.toAmountText().orEmpty()) }
    var currency by remember { mutableStateOf(initial?.currency ?: "EUR") }
    var showErrors by remember { mutableStateOf(false) }

    // One budget per category (and one global one): hide the ones already used when creating.
    val taken = existing.filter { it.id != initial?.id }.mapNotNull { it.category }.toSet()
    val globalTaken = existing.any { it.id != initial?.id && it.category == null }
    val available = DefaultCategories.filterNot { it in taken }
    val missingCategory = category == null && globalTaken

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "New budget" else "Edit budget", style = MaterialTheme.typography.titleLarge)
            DropdownField(
                "Applies to", category, available, { it }, { category = it },
                allowNone = !globalTaken, noneLabel = "All spending (monthly)", isError = showErrors && missingCategory,
            )
            if (missingCategory && (showErrors || available.isEmpty())) {
                Text(
                    if (available.isEmpty()) "Every category already has a budget. Edit an existing one instead."
                    else "You already have a monthly budget. Pick a category.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(amount, { amount = it }, Modifier.weight(1.4f), isError = showErrors && amount.toAmountOrNull() == null)
                DropdownField("Currency", currency, SupportedCurrencies, { it }, { currency = it ?: "EUR" }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val value = amount.toAmountOrNull()
                    if (value == null || missingCategory) showErrors = true
                    else {
                        onSave(Budget(initial?.id ?: 0, category, value, currency))
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            if (initial != null && onDelete != null) {
                OutlinedButton(onClick = { onDelete(initial); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete budget", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
