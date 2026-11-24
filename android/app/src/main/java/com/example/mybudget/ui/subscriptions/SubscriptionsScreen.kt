package com.example.mybudget.ui.subscriptions

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
import androidx.compose.material3.OutlinedTextField
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
import com.example.mybudget.ui.components.CategoryBadge
import com.example.mybudget.ui.components.DateField
import com.example.mybudget.ui.components.DropdownField
import com.example.mybudget.ui.components.EmptyState
import com.example.mybudget.ui.components.formatDay
import com.example.mybudget.ui.components.formatMoney
import com.example.mybudget.ui.components.toAmountOrNull
import com.example.mybudget.ui.components.toAmountText
import com.example.mybudget.ui.model.DefaultCategories
import com.example.mybudget.ui.model.Period
import com.example.mybudget.ui.model.SupportedCurrencies
import com.example.mybudget.ui.model.Subscription
import com.example.mybudget.ui.model.daysUntilNext
import com.example.mybudget.ui.model.monthlyEur
import com.example.mybudget.ui.theme.DeepGreen
import com.example.mybudget.ui.theme.Mint
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

@Composable
fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    onClick: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = DeepGreen)) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your bills & subscriptions cost", style = MaterialTheme.typography.bodyMedium, color = Mint)
                    Text(formatMoney(subscriptions.sumOf { it.monthlyEur() }, "EUR") + " / month", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("≈ ${formatMoney(subscriptions.sumOf { it.monthlyEur() } * 12, "EUR")} per year", style = MaterialTheme.typography.bodyMedium, color = Mint)
                }
            }
        }
        if (subscriptions.isEmpty()) {
            item { EmptyState("No subscriptions yet", "Add Netflix, rent, phone plan… so nothing sneaks up on you.") }
        }
        items(subscriptions.sortedBy { it.nextDate }, key = { it.id }) { sub ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { onClick(sub) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(sub.category ?: "Subscriptions")
                    Column(Modifier.weight(1f)) {
                        Text(sub.name, style = MaterialTheme.typography.titleMedium)
                        val d = sub.daysUntilNext()
                        val due = when {
                            d < 0 -> "Overdue"
                            d == 0L -> "Due today"
                            d == 1L -> "Due tomorrow"
                            else -> "Next: ${formatDay(sub.nextDate)}"
                        }
                        Text(due, style = MaterialTheme.typography.bodyMedium, color = if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMoney(sub.amount, sub.currency), style = MaterialTheme.typography.titleMedium)
                        Text(sub.period.label.lowercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionEditorSheet(
    initial: Subscription?,
    onSave: (Subscription) -> Unit,
    onDelete: ((Subscription) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amount by remember { mutableStateOf(initial?.amount?.toAmountText().orEmpty()) }
    var currency by remember { mutableStateOf(initial?.currency ?: "EUR") }
    var category by remember { mutableStateOf(initial?.category ?: "Subscriptions") }
    var period by remember { mutableStateOf(initial?.period ?: Period.MONTHLY) }
    var nextDate by remember { mutableStateOf(initial?.nextDate ?: LocalDate.now().plusMonths(1)) }
    var showErrors by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "New subscription" else "Edit subscription", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, isError = showErrors && name.isBlank(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(amount, { amount = it }, Modifier.weight(1.4f), isError = showErrors && amount.toAmountOrNull() == null)
                DropdownField("Currency", currency, SupportedCurrencies, { it }, { currency = it ?: "EUR" }, Modifier.weight(1f))
            }
            DropdownField("Repeats", period, Period.entries, { it.label }, { period = it ?: Period.MONTHLY })
            DropdownField("Category", category, DefaultCategories, { it }, { category = it ?: "Subscriptions" })
            DateField("Next payment", nextDate, { nextDate = it })
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val value = amount.toAmountOrNull()
                    if (name.isBlank() || value == null) showErrors = true
                    else {
                        onSave(Subscription(initial?.id ?: 0, name.trim(), value, currency, category, period, nextDate))
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            if (initial != null && onDelete != null) {
                OutlinedButton(onClick = { onDelete(initial); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete subscription", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
