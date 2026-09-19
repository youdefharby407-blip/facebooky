package com.yousef.facebooky.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFFFF3B6B)
val CallGreen = Color(0xFF30D158)
val CallRed = Color(0xFFFF453A)

/** Glass bars over the wallpaper. */
val BarColor = Color(0xD9000000)
/** Other people's bubbles: dark, slightly see-through. */
val TheirBubble = Color(0xE61F1F23)

private val Colors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1022),
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFFF8FAB),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1C1C1F),
    onSurfaceVariant = Color(0xFF9C9CA6),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF161618),
    surfaceContainerHigh = Color(0xFF1C1C1F),
    outlineVariant = Color(0xFF2C2C30),
    error = CallRed,
)

/** "My Space" is always dark to match the wallpaper. */
@Composable
fun FaceBookyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
