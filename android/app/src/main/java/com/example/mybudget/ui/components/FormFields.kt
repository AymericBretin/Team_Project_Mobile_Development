package com.example.mybudget.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Generic dropdown for a fixed list of choices. [allowNone] adds a "None" entry that returns null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    value: T?,
    options: List<T>,
    display: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
    noneLabel: String = "None",
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value?.let(display) ?: if (allowNone) noneLabel else "",
            onValueChange = {},
            readOnly = true,
            isError = isError,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowNone) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); expanded = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
fun AmountField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        // accept digits and one decimal separator only
        onValueChange = { new -> if (new.matches(Regex("^\\d*[.,]?\\d{0,2}$"))) onValueChange(new) },
        label = { Text("Amount") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

fun String.toAmountOrNull(): Double? = replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

/** Initial text of an [AmountField]: plain notation, no trailing zeros ("10", "13.99", never "1.0E7"). */
fun Double.toAmountText(): String = toBigDecimal().stripTrailingZeros().toPlainString()

/** Read-only field that opens a Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate, onValueChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = formatDay(value),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick a date") },
        interactionSource = remember { MutableInteractionSource() },
        modifier = modifier.fillMaxWidth().clickable { open = true },
        enabled = false,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
            disabledLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}
