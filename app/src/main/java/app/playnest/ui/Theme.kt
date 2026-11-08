package app.playnest.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFFF5A5F),
    onPrimary = Color.White,
    secondary = Color(0xFF7DD3C0),
    background = Color(0xFF0F0F12),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF17171C),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF24242B),
    onSurfaceVariant = Color(0xFFA9A9B4),
)

@Composable
fun PlayNestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
