package com.example.mybudget.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mybudget.ui.components.AppLocale
import com.example.mybudget.ui.components.BudgetBar
import com.example.mybudget.ui.components.EmptyState
import com.example.mybudget.ui.components.ExpenseRow
import com.example.mybudget.ui.components.SectionTitle
import com.example.mybudget.ui.components.LocalCategoryStyles
import com.example.mybudget.ui.components.formatMoney
import com.example.mybudget.ui.model.Budget
import com.example.mybudget.ui.model.BudgetUsage
import com.example.mybudget.ui.model.CurrencyConverter
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.Subscription
import com.example.mybudget.ui.model.byCategory
import com.example.mybudget.ui.model.daysUntilNext
import com.example.mybudget.ui.model.global
import com.example.mybudget.ui.model.homeAmount
import com.example.mybudget.ui.model.inMonth
import com.example.mybudget.ui.model.monthlyTotals
import com.example.mybudget.ui.model.remaining
import com.example.mybudget.ui.model.total
import com.example.mybudget.ui.model.usage
import com.example.mybudget.ui.theme.Amber
import com.example.mybudget.ui.theme.DeepGreen
import com.example.mybudget.ui.theme.Mint
import java.time.YearMonth
import java.time.format.TextStyle

private const val TREND_MONTHS = 6

/** Month overview: total spent against the budget, a 6-month trend, the breakdown by category and what's coming up. */
@Composable
fun DashboardScreen(
    greetingName: String?,
    expenses: List<Expense>,
    subscriptions: List<Subscription>,
    budgets: List<Budget>,
    converter: CurrencyConverter,
    onSeeAllExpenses: () -> Unit,
    onSeeSubscriptions: () -> Unit,
    onSeeBudgets: () -> Unit,
    onExpenseClick: (Expense) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thisMonth = YearMonth.now()
    var month by rememberSaveable { mutableStateOf(thisMonth) }
    // Can go back to the first month with an expense, never into the future.
    val firstMonth = expenses.minOfOrNull { YearMonth.from(it.date) }?.coerceAtMost(thisMonth) ?: thisMonth

    val home = converter.home
    val monthExpenses = expenses.inMonth(month)
    val spent = monthExpenses.total(converter)
    val budget = budgets.global()
    val breakdown = monthExpenses.byCategory(converter)
    // The chart ends with the current month, unless the user went further back.
    val trendEnd = if (month > thisMonth.minusMonths(TREND_MONTHS.toLong())) thisMonth else month
    val trend = expenses.monthlyTotals(converter, trendEnd, TREND_MONTHS)
    // Budgets at 80 % or more, worst first: worth a look before spending more.
    val warnings = budgets.map { it.usage(expenses, month, converter) }.filter { it.isNearLimit }.sortedByDescending { it.ratio }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
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
                Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonthSwitcher(
                        month = month,
                        canGoBack = month > firstMonth,
                        canGoForward = month < thisMonth,
                        onChange = { month = it },
                    )
                    Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (month == thisMonth) "Spent this month" else "Spent in ${monthName(month)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mint,
                        )
                        Text(formatMoney(spent, home), style = MaterialTheme.typography.displaySmall, color = Color.White)
                        if (budget != null) {
                            Spacer(Modifier.height(4.dp))
                            BudgetBar((spent / budget.homeAmount(converter)).toFloat(), trackColor = Color.White.copy(alpha = 0.2f))
                            val left = budget.remaining(spent, converter)
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
        }

        if (warnings.isNotEmpty()) {
            item { BudgetWarnings(warnings, onClick = onSeeBudgets) }
        }

        item { SectionTitle("Last $TREND_MONTHS months") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                MonthlyTrend(
                    totals = trend,
                    selected = month,
                    budget = budget?.homeAmount(converter),
                    currency = home,
                    onSelect = { month = it },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { SectionTitle("Where it goes") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                if (breakdown.isEmpty()) {
                    EmptyState(
                        if (month == thisMonth) "No expenses yet" else "No expenses in ${monthName(month)}",
                        "Add an expense with the + button.",
                    )
                } else {
                    CategoryDonut(breakdown, home, Modifier.padding(16.dp))
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
                                            d < 0 -> "Overdue"
                                            d == 0L -> "Due today"
                                            d == 1L -> "Tomorrow"
                                            else -> "In $d days"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                SectionTitle(if (month == thisMonth) "Recent" else "In ${monthName(month)}", Modifier.weight(1f))
                TextButton(onClick = onSeeAllExpenses) { Text("See all") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    monthExpenses.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }).take(5).forEach {
                        ExpenseRow(it, converter, onClick = { onExpenseClick(it) })
                    }
                    if (monthExpenses.isEmpty()) EmptyState("Nothing here yet", "Expenses of this month will show up here.")
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) } // room for the FAB
    }
}

/** Budgets close to or past their limit for the month shown. Tapping opens the Budgets tab. */
@Composable
private fun BudgetWarnings(warnings: List<BudgetUsage>, onClick: () -> Unit) {
    val anyOver = warnings.any { it.isOver }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (anyOver) MaterialTheme.colorScheme.errorContainer else Amber.copy(alpha = 0.18f),
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = if (anyOver) MaterialTheme.colorScheme.error else Amber,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                warnings.forEach { usage ->
                    val name = usage.budget.category ?: "Monthly budget"
                    Text(
                        if (usage.isOver) "$name: over budget (${(usage.ratio * 100).toInt()}%)"
                        else "$name: ${(usage.ratio * 100).toInt()}% used",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (anyOver) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun monthName(month: YearMonth, style: TextStyle = TextStyle.FULL): String =
    month.month.getDisplayName(style, AppLocale)

@Composable
private fun MonthSwitcher(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onChange: (YearMonth) -> Unit) {
    val colors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.tertiary,
        disabledContentColor = Color.White.copy(alpha = 0.25f),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val label = monthName(month) + if (month.year != YearMonth.now().year) " ${month.year}" else ""
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(month.minusMonths(1)) }, enabled = canGoBack, colors = colors) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = canGoForward, colors = colors) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** One bar per month (tap to open that month). The dashed line is the monthly budget. */
@Composable
private fun MonthlyTrend(
    totals: List<Pair<YearMonth, Double>>,
    selected: YearMonth,
    budget: Double?,
    currency: String,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chartHeight = 120.dp
    val max = maxOf(totals.maxOf { it.second }, budget ?: 0.0).takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val budgetColor = MaterialTheme.colorScheme.tertiary

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val selectedTotal = totals.firstOrNull { it.first == selected }?.second
        if (selectedTotal != null) {
            Text(
                "${monthName(selected)}: ${formatMoney(selectedTotal, currency)}" +
                    (budget?.let { " · budget ${formatMoney(it, currency)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            Row(Modifier.fillMaxWidth().height(chartHeight), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                totals.forEach { (month, total) ->
                    val isSelected = month == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(month) }
                            .semantics {
                                contentDescription = "${monthName(month)}: ${formatMoney(total, currency)}"
                                this.selected = isSelected
                            },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                // Keep a stub for empty months, and make any spending visible next to them.
                                .fillMaxHeight(if (total > 0) (total / max).toFloat().coerceIn(0.05f, 1f) else 0.015f)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (isSelected) barColor else barColor.copy(alpha = 0.3f)),
                        )
                    }
                }
            }
            if (budget != null && budget > 0) {
                Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                    val y = size.height * (1 - (budget / max).toFloat())
                    drawLine(
                        color = budgetColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            totals.forEach { (month, _) ->
                Text(
                    monthName(month, TextStyle.SHORT),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (month == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CategoryDonut(data: List<Pair<String, Double>>, currency: String, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }
    val styles = LocalCategoryStyles.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(130.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                var start = -90f
                data.forEach { (cat, value) ->
                    val sweep = (value / total * 360.0).toFloat()
                    drawArc(
                        color = styles.color(cat),
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
            Text(formatMoney(total, currency), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.take(5).forEach { (cat, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(styles.color(cat)))
                    Text(cat, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${(value / total * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
