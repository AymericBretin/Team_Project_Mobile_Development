package com.example.mybudget.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mybudget.ui.components.CategoryBadge
import com.example.mybudget.ui.components.CategoryColors
import com.example.mybudget.ui.components.CategoryIcons
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.DefaultCategories

/** Profile section listing the user's own categories, with a button to add one. */
@Composable
fun CategoriesSection(
    categories: List<CustomCategory>,
    onCategoryClick: (CustomCategory) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My categories", style = MaterialTheme.typography.titleMedium)
            if (categories.isEmpty()) {
                Text(
                    "Add your own categories (rent, pets, gifts…) next to the built-in ones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            categories.forEach { category ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCategoryClick(category) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CategoryBadge(category.name)
                    Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add a category")
            }
        }
    }
}

/** Bottom sheet to create ([initial] == null) or edit a custom category: name, color and icon. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorSheet(
    initial: CustomCategory?,
    existing: List<CustomCategory>,
    onSave: (CustomCategory) -> Unit,
    onDelete: (CustomCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: CategoryColors.keys.first()) }
    var icon by remember { mutableStateOf(initial?.icon ?: CategoryIcons.keys.first()) }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val trimmed = name.trim()
    val taken = (DefaultCategories + existing.filter { it.id != initial?.id }.map { it.name })
        .any { it.equals(trimmed, ignoreCase = true) }
    val error = when {
        trimmed.isEmpty() -> "Give it a name"
        trimmed.length > 30 -> "30 characters at most"
        taken -> "This category already exists"
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "New category" else "Edit category", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = showErrors && error != null,
                supportingText = { if (showErrors && error != null) Text(error) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Color", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryColors.forEach { (key, value) ->
                    val selected = key == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(value)
                            .border(3.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .clickable { color = key }
                            .semantics { contentDescription = "Color $key"; this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Text("Icon", style = MaterialTheme.typography.labelLarge)
            val tint = CategoryColors[color] ?: MaterialTheme.colorScheme.primary
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryIcons.forEach { (key, vector) ->
                    val selected = key == icon
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) tint.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, if (selected) tint else Color.Transparent, CircleShape)
                            .clickable { icon = key }
                            .semantics { this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(vector, contentDescription = "Icon $key", tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (error != null) showErrors = true
                    else {
                        onSave(CustomCategory(initial?.id ?: 0, trimmed, color, icon))
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            if (initial != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete category", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${initial.name}”?") },
            text = { Text("Its expenses and bills will have no category any more, and its budget will be removed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(initial); onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
