package com.gstore.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = PrimaryViolet,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryVioletDark,
    onPrimaryContainer = TextPrimary,
    secondary = AccentCyan,
    onSecondary = Background,
    tertiary = AccentLime,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    outline = OutlineDark,
)

private val LightColors = lightColorScheme(
    primary = PrimaryViolet,
    secondary = AccentCyan,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
)

/**
 * Tema da G Store. Dark mode por padrão (identidade da loja),
 * com fallback para o tema claro quando o sistema estiver claro.
 */
@Composable
fun GStoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
