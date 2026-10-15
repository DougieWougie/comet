package dev.dougie.comet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Space = Color(0xFF05060F)
val SpaceDeep = Color(0xFF0B1030)
val PingColor = Color(0xFFFFB454)
val DownloadColor = Color(0xFF4DD8FF)
val UploadColor = Color(0xFFD66BFF)
val Starlight = Color(0xFFE8EEFF)

private val DarkScheme = darkColorScheme(
    primary = DownloadColor,
    secondary = UploadColor,
    tertiary = PingColor,
    background = Space,
    surface = Color(0xFF10162E),
    onBackground = Starlight,
    onSurface = Starlight,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF007FA8),
    secondary = Color(0xFF9B2FD0),
    tertiary = Color(0xFFB86E00),
    background = Color(0xFFDCE9FF),
    surface = Color(0xFFF4F8FF),
    onBackground = Color(0xFF0B1030),
    onSurface = Color(0xFF0B1030),
)

@Composable
fun CometTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme) {
        // Surface supplies the default text colour; without it Text falls back to black.
        Surface(color = scheme.background, contentColor = scheme.onBackground, content = content)
    }
}
