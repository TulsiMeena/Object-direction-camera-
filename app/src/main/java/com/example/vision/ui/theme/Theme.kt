package com.example.vision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VisionTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    )
)

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
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = VisionTypography,
        content = content
    )
}
