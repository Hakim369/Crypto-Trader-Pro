package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF04101A),
    primaryContainer = Color(0xFF00363A),
    onPrimaryContainer = CyanAccent,
    secondary = EmeraldBull,
    onSecondary = Color(0xFF003915),
    secondaryContainer = EmeraldBullDim,
    onSecondaryContainer = Color(0xFFB9F6CA),
    tertiary = VioletAccent,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = SlateTextPrimary,
    surface = DarkSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = SlateTextSecondary,
    outline = DarkSurfaceBorder,
    error = CrimsonBear,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force high-contrast terminal dark mode for market automation
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
