package com.dam2.flashdownloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Esquema de colores para el tema claro
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF018786),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFF6B6B),
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

/**
 * Esquema de colores para el tema oscuro
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFE1BFFF),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFF6FF6EB),
    tertiary = Color(0xFFFF6B6B),
    onTertiary = Color.Black,
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF2B2930),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

/**
 * Tema de la aplicación para Android
 */
@Composable
fun FlashDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

/**
 * Colores personalizados para estados de descarga
 */
object DownloadColors {
    val Queued = Color(0xFF9E9E9E)
    val Downloading = Color(0xFF2196F3)
    val Paused = Color(0xFFFFA726)
    val Completed = Color(0xFF4CAF50)
    val Failed = Color(0xFFF44336)
    val Cancelled = Color(0xFF607D8B)
}

/**
 * Colores para categorías
 */
object CategoryColors {
    val Documents = Color(0xFF1976D2)
    val Multimedia = Color(0xFFE91E63)
    val Software = Color(0xFF9C27B0)
    val Compressed = Color(0xFFFF9800)
    val Others = Color(0xFF607D8B)
}
