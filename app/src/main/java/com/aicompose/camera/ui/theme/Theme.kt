package com.aicompose.camera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============ 专业相机深色主题（Mola 风格参考） ============
val Amber = Color(0xFFFFC107)
val AmberBright = Color(0xFFFFD54F)
val Teal = Color(0xFF26A69A)
val AccentGreen = Color(0xFF4CAF50)
val BackgroundDark = Color(0xFF0A0A0C)
val SurfaceDark = Color(0xFF14161A)
val SurfaceDark2 = Color(0xFF1E2126)
val TextPrimary = Color(0xFFF2F4F6)
val TextSecondary = Color(0xFF9AA3AB)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF231A00),
    secondary = Teal,
    onSecondary = Color.White,
    tertiary = AccentGreen,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3F45)
)

@Composable
fun AIComposeCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
