package com.example.mybudget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = MintBackground,
    onPrimaryContainer = DeepGreen,
    secondary = EmeraldDark,
    tertiary = Amber,
    error = Coral,
    background = Color(0xFFF7FBF9),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = MintBackground,
    onSurfaceVariant = Slate,
    outline = Color(0xFFC5D6CF),
    secondaryContainer = Color(0xFFCDEBDD),
    onSecondaryContainer = DeepGreen,
    surfaceTint = Emerald,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F8F5),
    surfaceContainer = Color(0xFFEAF4EF),
    surfaceContainerHigh = Color(0xFFE3EFE9),
    surfaceContainerHighest = Color(0xFFDCE9E3),
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = DeepGreen,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = MintBackground,
    secondary = Mint,
    tertiary = Amber,
    error = Coral,
    background = Color(0xFF0B2A22),
    onBackground = MintBackground,
    surface = Color(0xFF123A2F),
    onSurface = MintBackground,
    surfaceVariant = Color(0xFF1A4A3D),
    onSurfaceVariant = Mint,
    outline = Color(0xFF3E6B5D),
)

/**
 * Dynamic (wallpaper) colors are off on purpose: the brand palette should stay the same on every phone.
 */
@Composable
fun MyBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
