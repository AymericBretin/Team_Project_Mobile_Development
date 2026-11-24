package com.example.mybudget.ui.expenses

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
import com.example.mybudget.ui.components.EmptyState
import com.example.mybudget.ui.components.ExpenseRow
import com.example.mybudget.ui.components.formatDay
import com.example.mybudget.ui.components.formatMoney
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.amountEur
import com.example.mybudget.ui.model.totalEur

/** History with free-text search (title, category, tags) and category filter chips, grouped by day. */
@Composable
fun ExpensesScreen(
    expenses: List<Expense>,
    onExpenseClick: (Expense) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val categories = expenses.mapNotNull { it.category }.distinct().sorted()
    val filtered = expenses
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
            item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All") }) }
            items(categories) { cat ->
                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = if (selectedCategory == cat) null else cat }, label = { Text(cat) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("${filtered.size} expense${if (filtered.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Total ${formatMoney(filtered.totalEur(), "EUR")}", style = MaterialTheme.typography.labelLarge)
        }

        if (filtered.isEmpty()) {
            EmptyState("No expenses found", if (expenses.isEmpty()) "Tap + to add your first expense." else "Try another search or filter.")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filtered.groupBy { it.date }.forEach { (date, items) ->
                    item(key = "h-$date") {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                            Text(formatDay(date).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("-" + formatMoney(items.sumOf { it.amountEur() }, "EUR"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "g-$date") {
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                items.forEach { ExpenseRow(it, onClick = { onExpenseClick(it) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}
