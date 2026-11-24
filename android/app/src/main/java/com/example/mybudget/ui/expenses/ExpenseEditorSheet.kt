package com.example.mybudget.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mybudget.ui.components.AmountField
import com.example.mybudget.ui.components.DateField
import com.example.mybudget.ui.components.DropdownField
import com.example.mybudget.ui.components.toAmountOrNull
import com.example.mybudget.ui.components.toAmountText
import com.example.mybudget.ui.model.DefaultCategories
import com.example.mybudget.ui.model.Expense
import com.example.mybudget.ui.model.SupportedCurrencies
import java.time.LocalDate

/** Bottom sheet to add ([initial] == null) or edit an expense. Tags are typed comma separated. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorSheet(
    initial: Expense?,
    onSave: (Expense) -> Unit,
    onDelete: ((Expense) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var amount by remember { mutableStateOf(initial?.amount?.toAmountText().orEmpty()) }
    var currency by remember { mutableStateOf(initial?.currency ?: "EUR") }
    var category by remember { mutableStateOf(initial?.category) }
    var tags by remember { mutableStateOf(initial?.tags?.joinToString(", ").orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var showErrors by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "New expense" else "Edit expense", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                isError = showErrors && title.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(amount, { amount = it }, Modifier.weight(1.4f), isError = showErrors && amount.toAmountOrNull() == null)
                DropdownField("Currency", currency, SupportedCurrencies, { it }, { currency = it ?: "EUR" }, Modifier.weight(1f))
            }
            DropdownField("Category", category, DefaultCategories, { it }, { category = it }, allowNone = true, noneLabel = "No category")
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField("Date", date, { date = it })

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val value = amount.toAmountOrNull()
                    if (title.isBlank() || value == null) {
                        showErrors = true
                    } else {
                        onSave(
                            Expense(
                                id = initial?.id ?: 0,
                                title = title.trim(),
                                amount = value,
                                currency = currency,
                                category = category,
                                tags = tags.split(',').map { it.trim().trimStart('#') }.filter { it.isNotEmpty() },
                                date = date,
                            ),
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }

            if (initial != null && onDelete != null) {
                OutlinedButton(
                    onClick = { onDelete(initial); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete expense", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
