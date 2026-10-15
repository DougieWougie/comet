package dev.dougie.comet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Space = Color(0xFF05060F)
val SpaceDeep = Color(0xFF0B1030)
val PingColor = Color(0xFFFFB454)
val DownloadColor = Color(0xFF4DD8FF)
val UploadColor = Color(0xFFD66BFF)
val Starlight = Color(0xFFE8EEFF)

private val Scheme = darkColorScheme(
    primary = DownloadColor,
    secondary = UploadColor,
    tertiary = PingColor,
    background = Space,
    surface = Color(0xFF10162E),
    onBackground = Starlight,
    onSurface = Starlight,
)

@Composable
fun CometTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        // Surface supplies the default text colour; without it Text falls back to black.
        Surface(color = Space, contentColor = Starlight, content = content)
    }
}
