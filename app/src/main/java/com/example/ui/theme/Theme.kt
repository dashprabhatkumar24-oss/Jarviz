package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyanPrimary,
    onPrimary = Color(0xFF002633),
    primaryContainer = Color(0xFF003D52),
    onPrimaryContainer = JarvisCyanBright,
    secondary = JarvisBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0E2A5C),
    onSecondaryContainer = Color(0xFFB8D4FF),
    tertiary = JarvisAmberWarning,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = Color(0xFF4D3300),
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = JarvisCrimsonAlert,
    onError = Color.White,
    background = JarvisDeepSpace,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurfaceDark,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceElevated,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorderCyan
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = JarvisLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2F0FF),
    onPrimaryContainer = Color(0xFF002633),
    secondary = JarvisLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4FF),
    onSecondaryContainer = Color(0xFF001B3D),
    tertiary = JarvisLightTertiary,
    onTertiary = Color.White,
    background = JarvisLightBackground,
    onBackground = JarvisLightTextPrimary,
    surface = JarvisLightSurface,
    onSurface = JarvisLightTextPrimary,
    surfaceVariant = JarvisLightSurfaceVariant,
    onSurfaceVariant = JarvisLightTextSecondary,
    outline = Color(0xFF007799)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JarvisDarkColorScheme else JarvisLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
