package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = CyanDark,
    onPrimaryContainer = Color.White,
    secondary = AmberAccent,
    onSecondary = Color.Black,
    secondaryContainer = AmberGlow,
    onSecondaryContainer = Color.Black,
    tertiary = BlueMoving,
    onTertiary = Color.Black,
    background = IndigoDark,
    onBackground = TextPrimary,
    surface = IndigoSurface,
    onSurface = TextPrimary,
    surfaceVariant = IndigoSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = IndigoBorder,
    error = RedAlert,
    onError = Color.White
)

@Composable
fun AdvancedVisionTheme(
    darkTheme: Boolean = true, // Default to dark futuristic theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
