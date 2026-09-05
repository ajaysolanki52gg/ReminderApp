package com.reminderapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 🎨 ReminderApp Color Schemes
 */

fun LightColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = AppPrimary,
        onPrimary = AppOnPrimary,
        primaryContainer = AppPrimaryContainer,
        onPrimaryContainer = AppOnPrimaryContainer,
        secondary = AppAccentAI,
        onSecondary = AppOnPrimary,
        secondaryContainer = AppAccentAIContainer,
        onSecondaryContainer = AppOnAccentAIContainer,
        background = AppBackground,
        onBackground = AppOnSurface,
        surface = AppSurface,
        onSurface = AppOnSurface,
        surfaceVariant = AppBackground, // Use background for variant for subtle contrast
        onSurfaceVariant = AppOnSurfaceVariant,
        outline = AppOutline,
        error = AppMissed,
        onError = AppOnPrimary,
        errorContainer = AppMissedContainer,
        onErrorContainer = AppOnMissedContainer,
    )
}

fun DarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = AppPrimaryDark,
        onPrimary = AppOnPrimaryDark,
        primaryContainer = AppPrimaryContainerDark,
        onPrimaryContainer = AppOnPrimaryContainerDark,
        background = AppBackgroundDark,
        onBackground = AppOnSurfaceDark,
        surface = AppSurfaceDark,
        onSurface = AppOnSurfaceDark,
        surfaceVariant = AppSurfaceDark,
        onSurfaceVariant = AppOnSurfaceVariantDark,
        outline = AppOutlineDark,
        error = AppMissed, // Keep coral for error in dark mode or shift slightly
        onError = AppOnPrimary,
        errorContainer = AppMissedContainer,
        onErrorContainer = AppOnMissedContainer,
    )
}
