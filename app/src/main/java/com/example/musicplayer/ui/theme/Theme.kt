package com.example.musicplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Cyan80,
    onPrimary = Color.Black,
    primaryContainer = Cyan40,
    onPrimaryContainer = Color.White,

    secondary = Purple60,
    onSecondary = Color.White,
    secondaryContainer = Purple40,
    onSecondaryContainer = Color.White,

    tertiary = Pink60,
    onTertiary = Color.White,
    tertiaryContainer = Pink40,
    onTertiaryContainer = Color.White,

    background = Surface0,
    onBackground = TextPrimary,

    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHighest = Surface3,

    error = Error,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan60,
    onPrimary = Color.White,
    primaryContainer = Cyan80,
    onPrimaryContainer = Color.Black,

    secondary = Purple60,
    onSecondary = Color.White,
    secondaryContainer = Purple80,
    onSecondaryContainer = Color.Black,

    tertiary = Pink60,
    onTertiary = Color.White,
    tertiaryContainer = Pink80,
    onTertiaryContainer = Color.Black,

    background = LightSurface0,
    onBackground = Color.Black,

    surface = LightSurface1,
    onSurface = Color.Black,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = Color(0xFF666666)
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Changed to false for consistent branding
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}