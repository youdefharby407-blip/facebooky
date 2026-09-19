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

/** Per-room chat backgrounds. "wallpaper" = the bundled image; the rest are solid/gradient. */
data class ChatTheme(val id: String, val label: String, val colors: List<Color>)

val ChatThemes = listOf(
    ChatTheme("wallpaper", "Default", listOf(Color(0xFF0A0A0A), Color(0xFF141414))),
    ChatTheme("midnight", "Midnight", listOf(Color(0xFF0B1026), Color(0xFF1B2452))),
    ChatTheme("rose", "Rose", listOf(Color(0xFF2A0A16), Color(0xFF4A1022))),
    ChatTheme("forest", "Forest", listOf(Color(0xFF06160F), Color(0xFF0F2A1E))),
    ChatTheme("plum", "Plum", listOf(Color(0xFF160A26), Color(0xFF2A1452))),
    ChatTheme("charcoal", "Charcoal", listOf(Color(0xFF121212), Color(0xFF1E1E1E))),
    ChatTheme("ocean", "Ocean", listOf(Color(0xFF04141A), Color(0xFF0A2A38))),
)

fun chatThemeById(id: String): ChatTheme = ChatThemes.firstOrNull { it.id == id } ?: ChatThemes.first()
