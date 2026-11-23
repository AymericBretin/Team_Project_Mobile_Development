package com.example.mybudget.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mybudget.ui.components.BudgetBar
import com.example.mybudget.ui.components.EmptyState
import com.example.mybudget.ui.components.ExpenseRow
import com.example.mybudget.ui.components.SectionTitle
import com.example.mybudget.ui.components.categoryColor
import com.example.mybudget.ui.components.formatMoney
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Subscription
import com.example.mybudget.ui.model.byCategory
import com.example.mybudget.ui.model.daysUntilNext
import com.example.mybudget.ui.model.global
import com.example.mybudget.ui.model.inMonth
import com.example.mybudget.ui.model.totalEur
import com.example.mybudget.ui.theme.DeepGreen
import com.example.mybudget.ui.theme.Mint
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    greetingName: String?,
    expenses: List<Expense>,
    subscriptions: List<Subscription>,
    budgets: List<Budget>,
    onSeeAllExpenses: () -> Unit,
    onSeeSubscriptions: () -> Unit,
    onExpenseClick: (Expense) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = YearMonth.now()
    val monthExpenses = expenses.inMonth(month)
    val spent = monthExpenses.totalEur()
    val budget = budgets.global()
    val breakdown = monthExpenses.byCategory()

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Hi${greetingName?.let { ", $it" } ?: ""} 👋",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Hero card, same look as the phone mock-up in the deck.
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DeepGreen),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text("Spent this month", style = MaterialTheme.typography.bodyMedium, color = Mint)
                    Text(formatMoney(spent, "EUR"), style = MaterialTheme.typography.displaySmall, color = Color.White)
                    if (budget != null) {
                        Spacer(Modifier.height(4.dp))
                        BudgetBar((spent / budget.amount).toFloat(), trackColor = Color.White.copy(alpha = 0.2f))
                        val left = budget.amount - spent
                        Text(
                            if (left >= 0) "${formatMoney(left, budget.currency)} left of ${formatMoney(budget.amount, budget.currency)}"
                            else "${formatMoney(-left, budget.currency)} over your ${formatMoney(budget.amount, budget.currency)} budget",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (left >= 0) Mint else MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text("No monthly budget set yet", style = MaterialTheme.typography.bodyMedium, color = Mint)
                    }
                }
            }
        }

        item { SectionTitle("Where it goes") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                if (breakdown.isEmpty()) {
                    EmptyState("No expenses yet", "Add your first expense with the + button.")
                } else {
                    CategoryDonut(breakdown, Modifier.padding(16.dp))
                }
            }
        }

        val upcoming = subscriptions.sortedBy { it.nextDate }.take(3)
        if (upcoming.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Coming up", Modifier.weight(1f))
                    TextButton(onClick = onSeeSubscriptions) { Text("All bills") }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        upcoming.forEach { sub ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(sub.name, style = MaterialTheme.typography.titleMedium)
                                    val d = sub.daysUntilNext()
                                    Text(
                                        when {
                                            d <= 0 -> "Due today"
                                            d == 1L -> "Tomorrow"
                                            else -> "In $d days"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(formatMoney(sub.amount, sub.currency), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent", Modifier.weight(1f))
                TextButton(onClick = onSeeAllExpenses) { Text("See all") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    expenses.sortedByDescending { it.date }.take(5).forEach { ExpenseRow(it, onClick = { onExpenseClick(it) }) }
                    if (expenses.isEmpty()) EmptyState("Nothing here yet", "Your latest expenses will show up here.")
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) } // room for the FAB
    }
}

@Composable
private fun CategoryDonut(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(130.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                var start = -90f
                data.forEach { (cat, value) ->
                    val sweep = (value / total * 360.0).toFloat()
                    drawArc(
                        color = categoryColor(cat),
                        startAngle = start,
                        sweepAngle = (sweep - 2f).coerceAtLeast(0.5f), // small gap between slices
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Text(formatMoney(total, "EUR"), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.take(5).forEach { (cat, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(cat)))
                    Text(cat, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${(value / total * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
