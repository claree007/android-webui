package com.androidwebui.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.androidwebui.android.core.prefs.ThemeMode

// Neutral palette derived from the dark reference screenshots (docs/screenshot-map.md).
// The light palette is derived by mirroring it; no light reference exists.
private val LightColors = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF525252),
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    outline = Color(0xFFA3A3A3),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFB3261D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF171717),
    background = Color(0xFF171717),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF171717),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF262626),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFF2B8B5),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = AppShapes, content = content)
}
