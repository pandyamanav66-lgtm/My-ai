package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightM3Scheme = lightColorScheme(
    primary = LightDrstColors.ink,
    onPrimary = LightDrstColors.bg,
    primaryContainer = LightDrstColors.band2,
    onPrimaryContainer = LightDrstColors.onBand,
    secondary = LightDrstColors.gold,
    onSecondary = LightDrstColors.ink,
    secondaryContainer = LightDrstColors.surface2,
    onSecondaryContainer = LightDrstColors.goldInk,
    tertiary = LightDrstColors.ok,
    onTertiary = LightDrstColors.surface,
    background = LightDrstColors.bg,
    onBackground = LightDrstColors.ink,
    surface = LightDrstColors.surface,
    onSurface = LightDrstColors.ink,
    surfaceVariant = LightDrstColors.surface2,
    onSurfaceVariant = LightDrstColors.muted,
    outline = LightDrstColors.line,
    outlineVariant = LightDrstColors.track,
    error = LightDrstColors.badInk,
    onError = LightDrstColors.surface
)

private val DarkM3Scheme = darkColorScheme(
    primary = DarkDrstColors.gold,
    onPrimary = DarkDrstColors.band,
    primaryContainer = DarkDrstColors.band2,
    onPrimaryContainer = DarkDrstColors.onBand,
    secondary = DarkDrstColors.gold,
    onSecondary = DarkDrstColors.band,
    secondaryContainer = DarkDrstColors.surface2,
    onSecondaryContainer = DarkDrstColors.goldInk,
    tertiary = DarkDrstColors.ok,
    onTertiary = DarkDrstColors.band,
    background = DarkDrstColors.bg,
    onBackground = DarkDrstColors.ink,
    surface = DarkDrstColors.surface,
    onSurface = DarkDrstColors.ink,
    surfaceVariant = DarkDrstColors.surface2,
    onSurfaceVariant = DarkDrstColors.muted,
    outline = DarkDrstColors.line,
    outlineVariant = DarkDrstColors.track,
    error = DarkDrstColors.badInk,
    onError = DarkDrstColors.band
)

@Composable
fun StudyTrackerTheme(
    themeMode: String = "auto",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val drstColors = if (useDark) DarkDrstColors else LightDrstColors
    val m3Scheme = if (useDark) DarkM3Scheme else LightM3Scheme

    CompositionLocalProvider(LocalDrstColors provides drstColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = AppTypography,
            content = content
        )
    }
}
