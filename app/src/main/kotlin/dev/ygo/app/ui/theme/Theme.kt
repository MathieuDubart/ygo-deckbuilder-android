package dev.ygo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Les couleurs du classeur, reprises du web et de l'app iOS : des teintes de papier et de
 * carton plutôt que les bleus par défaut de Material, pour que les cartes restent les seules
 * choses colorées à l'écran.
 */
private val Accent = Color(0xFFB4793A)
private val Success = Color(0xFF4CBF85)
private val Warning = Color(0xFFEDA840)
val Danger = Color(0xFFEB5C54)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1C1A17),
    secondary = Color(0xFF8A8176),
    background = Color(0xFF090807),
    onBackground = Color(0xFFF7F5F1),
    surface = Color(0xFF161411),
    onSurface = Color(0xFFF7F5F1),
    surfaceVariant = Color(0xFF231F1A),
    onSurfaceVariant = Color(0xFFBDB4A7),
    error = Danger,
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF6B6459),
    background = Color(0xFFE5E2DB),
    onBackground = Color(0xFF1C1A17),
    surface = Color(0xFFF4F2ED),
    onSurface = Color(0xFF1C1A17),
    surfaceVariant = Color(0xFFDCD7CD),
    onSurfaceVariant = Color(0xFF554F46),
    error = Danger,
)

object Theme {
    val success = Success
    val warning = Warning
    val danger = Danger
}

@Composable
fun YgoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
