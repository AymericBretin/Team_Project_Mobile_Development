package com.example.mybudget.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mybudget.ui.theme.Amber
import com.example.mybudget.ui.theme.Coral
import com.example.mybudget.ui.theme.Emerald
import com.example.mybudget.ui.theme.EmeraldDark
import com.example.mybudget.ui.theme.Mint
import com.example.mybudget.ui.theme.Slate
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** "€1,245.00", "₩9,000" (no decimals for currencies that don't use them). */
fun formatMoney(amount: Double, currency: String): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { format.currency = Currency.getInstance(currency) }
    val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2)
    format.minimumFractionDigits = digits.coerceAtLeast(0)
    format.maximumFractionDigits = digits.coerceAtLeast(0)
    return format.format(amount)
}

fun formatDay(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
}

fun categoryColor(category: String?): Color = when (category) {
    "Groceries" -> Emerald
    "Restaurant" -> Amber
    "Subscriptions" -> Coral
    "Transport" -> EmeraldDark
    "Shopping" -> Color(0xFF7B8CDE)
    "Health" -> Color(0xFFD96C9C)
    "Leisure" -> Color(0xFF4FB3D9)
    "Travel" -> Mint
    else -> Slate
}

fun categoryIcon(category: String?): ImageVector = when (category) {
    "Groceries" -> Icons.Filled.LocalGroceryStore
    "Restaurant" -> Icons.Filled.Restaurant
    "Subscriptions" -> Icons.Filled.Autorenew
    "Transport" -> Icons.Filled.DirectionsBus
    "Shopping" -> Icons.Filled.ShoppingBag
    "Health" -> Icons.Filled.LocalHospital
    "Leisure" -> Icons.Filled.Theaters
    "Travel" -> Icons.Filled.Flight
    else -> Icons.Filled.MoreHoriz
}
