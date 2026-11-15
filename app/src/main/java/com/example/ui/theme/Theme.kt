package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MelTiViDarkColorScheme = darkColorScheme(
    primary = WarmOrange,
    onPrimary = ObsidianBg,
    primaryContainer = WarmOrangeSoft,
    onPrimaryContainer = WarmAmber,
    secondary = WarmAmber,
    onSecondary = ObsidianBg,
    secondaryContainer = CardSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = CardSurfaceElevated,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle,
    error = ErrorRed,
    onError = Color.White
)

/**
 * Основная тёмная тема приложения MelTiVi (активна по умолчанию на телефоне и ТВ).
 */
@Composable
fun MelTiViTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MelTiViDarkColorScheme,
        typography = MelTiViTypography,
        content = content
    )
}
