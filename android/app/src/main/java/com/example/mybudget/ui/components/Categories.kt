package com.example.mybudget.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mybudget.ui.model.CustomCategory
import com.example.mybudget.ui.model.DefaultCategories
import com.example.mybudget.ui.theme.Amber
import com.example.mybudget.ui.theme.Coral
import com.example.mybudget.ui.theme.Emerald
import com.example.mybudget.ui.theme.EmeraldDark
import com.example.mybudget.ui.theme.Mint
import com.example.mybudget.ui.theme.Slate

/** Colors a custom category can take. The key is what the server stores. */
val CategoryColors: Map<String, Color> = linkedMapOf(
    "emerald" to Emerald,
    "teal" to EmeraldDark,
    "mint" to Mint,
    "sky" to Color(0xFF4FB3D9),
    "indigo" to Color(0xFF7B8CDE),
    "violet" to Color(0xFF9C6ADE),
    "pink" to Color(0xFFD96C9C),
    "coral" to Coral,
    "amber" to Amber,
    "brown" to Color(0xFFA1785C),
    "slate" to Slate,
)

/** Icons a custom category can take. The key is what the server stores. */
val CategoryIcons: Map<String, ImageVector> = linkedMapOf(
    "tag" to Icons.Filled.Label,
    "home" to Icons.Filled.Home,
    "bolt" to Icons.Filled.Bolt,
    "phone" to Icons.Filled.Smartphone,
    "car" to Icons.Filled.DirectionsCar,
    "coffee" to Icons.Filled.LocalCafe,
    "bar" to Icons.Filled.LocalBar,
    "clothes" to Icons.Filled.Checkroom,
    "beauty" to Icons.Filled.Spa,
    "sport" to Icons.Filled.FitnessCenter,
    "games" to Icons.Filled.SportsEsports,
    "school" to Icons.Filled.School,
    "work" to Icons.Filled.Work,
    "gift" to Icons.Filled.CardGiftcard,
    "pets" to Icons.Filled.Pets,
    "kids" to Icons.Filled.ChildCare,
    "savings" to Icons.Filled.Savings,
)

/** Color and icon of any category by name: built-in ones, then the user's own, else a neutral style. */
class CategoryStyles(custom: List<CustomCategory> = emptyList()) {
    private val byName = custom.associateBy { it.name }

    /** Every category the user can pick, built-in ones first. */
    val names: List<String> = DefaultCategories + custom.map { it.name }

    fun color(category: String?): Color = builtInColor(category)
        ?: byName[category]?.let { CategoryColors[it.color] }
        ?: Slate

    fun icon(category: String?): ImageVector = builtInIcon(category)
        ?: byName[category]?.let { CategoryIcons[it.icon] }
        ?: Icons.Filled.MoreHoriz

    private fun builtInColor(category: String?): Color? = when (category) {
        "Groceries" -> Emerald
        "Restaurant" -> Amber
        "Subscriptions" -> Coral
        "Transport" -> EmeraldDark
        "Shopping" -> Color(0xFF7B8CDE)
        "Health" -> Color(0xFFD96C9C)
        "Leisure" -> Color(0xFF4FB3D9)
        "Travel" -> Mint
        else -> null
    }

    private fun builtInIcon(category: String?): ImageVector? = when (category) {
        "Groceries" -> Icons.Filled.LocalGroceryStore
        "Restaurant" -> Icons.Filled.Restaurant
        "Subscriptions" -> Icons.Filled.Autorenew
        "Transport" -> Icons.Filled.DirectionsBus
        "Shopping" -> Icons.Filled.ShoppingBag
        "Health" -> Icons.Filled.LocalHospital
        "Leisure" -> Icons.Filled.Theaters
        "Travel" -> Icons.Filled.Flight
        else -> null
    }
}

/** Provided by AppRoot from the user's categories, so every screen draws them the same way. */
val LocalCategoryStyles = staticCompositionLocalOf { CategoryStyles() }
