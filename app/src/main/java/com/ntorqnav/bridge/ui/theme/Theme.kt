package com.ntorqnav.bridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0F1117)
val DarkSurface = Color(0xFF181B24)
val DarkSurfaceVariant = Color(0xFF222634)
val PrimaryTeal = Color(0xFF00E5FF)
val SecondaryGreen = Color(0xFF00E676)
val AccentOrange = Color(0xFFFF9100)
val DangerRed = Color(0xFFFF5252)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    onPrimary = Color.Black,
    secondary = SecondaryGreen,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun NtorqNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
