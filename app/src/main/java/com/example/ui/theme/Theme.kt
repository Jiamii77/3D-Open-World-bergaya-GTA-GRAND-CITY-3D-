package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ViceDarkColorScheme =
  darkColorScheme(
    primary = NeonCyan,
    onPrimary = ViceMidnight,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyan,
    secondary = SunsetCoral,
    onSecondary = TextPrimary,
    secondaryContainer = SunsetCoralContainer,
    onSecondaryContainer = SunsetCoral,
    tertiary = ViceAmber,
    onTertiary = ViceMidnight,
    background = ViceMidnight,
    onBackground = TextPrimary,
    surface = ViceSurface,
    onSurface = TextPrimary,
    surfaceVariant = ViceSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ViceCardBorder,
    error = ViceCrimson,
    onError = TextPrimary,
  )

@Composable
fun ViceRoamTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = ViceDarkColorScheme,
    typography = ViceRoamTypography,
    content = content,
  )
}
