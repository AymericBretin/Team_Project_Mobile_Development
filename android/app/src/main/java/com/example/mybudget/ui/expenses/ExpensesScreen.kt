package com.example.mybudget.ui.expenses

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import com.example.mybudget.ui.model.DateFilter
import com.example.mybudget.ui.model.inPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mybudget.ui.components.AppLocale
import com.example.mybudget.ui.components.EmptyState
import com.example.mybudget.ui.components.ExpenseRow
import com.example.mybudget.ui.components.formatDay
import com.example.mybudget.ui.components.formatMoney
import com.example.mybudget.ui.model.CurrencyConverter
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.total

/** History with free-text search (title, category, tags) and category filter chips, grouped by day. */
@Composable
fun ExpensesScreen(
    expenses: List<Expense>,
    converter: CurrencyConverter,
    onExpenseClick: (Expense) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable(stateSaver = DateFilterSaver) { mutableStateOf<DateFilter>(DateFilter.AllTime) }
    var pickingRange by remember { mutableStateOf(false) }

    val categories = expenses.mapNotNull { it.category }.distinct().sorted()
    val filtered = expenses
        .inPeriod(period)
        .filter { selectedCategory == null || it.category == selectedCategory }
        .filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.category?.contains(query, ignoreCase = true) == true ||
                it.tags.any { tag -> tag.contains(query.trimStart('#'), ignoreCase = true) }
        }
        .sortedByDescending { it.date }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search expenses or #tags") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(DateFilter.AllTime, DateFilter.ThisMonth, DateFilter.LastMonth)) { option ->
                FilterChip(selected = period == option, onClick = { period = option }, label = { Text(option.label()) })
            }
            item {
                val custom = period as? DateFilter.Custom
                FilterChip(
                    selected = custom != null,
                    onClick = { pickingRange = true },
                    label = { Text(custom?.label() ?: "Pick dates") },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All categories") }) }
            items(categories) { cat ->
                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = if (selectedCategory == cat) null else cat }, label = { Text(cat) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("${filtered.size} expense${if (filtered.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Total ${formatMoney(filtered.total(converter), converter.home)}", style = MaterialTheme.typography.labelLarge)
        }

        if (pickingRange) {
            DateRangeDialog(
                initial = period as? DateFilter.Custom,
                onPick = { period = it; pickingRange = false },
                onDismiss = { pickingRange = false },
            )
        }

        if (filtered.isEmpty()) {
            EmptyState("No expenses found", if (expenses.isEmpty()) "Tap + to add your first expense." else "Try another search, period or category.")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filtered.groupBy { it.date }.forEach { (date, items) ->
                    item(key = "h-$date") {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                            Text(formatDay(date).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("-" + formatMoney(items.total(converter), converter.home), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "g-$date") {
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                items.forEach { ExpenseRow(it, converter, onClick = { onExpenseClick(it) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DateFilter.label(): String = when (this) {
    DateFilter.AllTime -> "All time"
    DateFilter.ThisMonth -> "This month"
    DateFilter.LastMonth -> "Last month"
    is DateFilter.Custom -> {
        val format = DateTimeFormatter.ofPattern("d MMM", AppLocale)
        if (start == end) start.format(format) else "${start.format(format)} – ${end.format(format)}"
    }
}

private val DateFilterSaver = Saver<DateFilter, String>(save = { DateFilter.encode(it) }, restore = { DateFilter.decode(it) })

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Picks a start and an end day (the end can be left empty for a single day). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(initial: DateFilter.Custom?, onPick: (DateFilter.Custom) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toUtcMillis(),
        initialSelectedEndDateMillis = initial?.end?.toUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.toLocalDate() ?: return@TextButton
                    onPick(DateFilter.Custom(start, state.selectedEndDateMillis?.toLocalDate() ?: start))
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f), title = { Text("Expenses between", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

