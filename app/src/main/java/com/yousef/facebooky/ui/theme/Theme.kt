package com.yousef.facebooky.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF2563EB)
val CallGreen = Color(0xFF22C55E)
val CallRed = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0B1B3F),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF15181D),
    surface = Color.White,
    onSurface = Color(0xFF15181D),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF5A6272),
    surfaceContainerLow = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6E9BFF),
    onPrimary = Color(0xFF071430),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDCE7FF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF15181D),
    onSurface = Color(0xFFE7E9EE),
    surfaceVariant = Color(0xFF232830),
    onSurfaceVariant = Color(0xFFA3AAB8),
    surfaceContainerLow = Color(0xFF15181D),
)

@Composable
fun FaceBookyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
