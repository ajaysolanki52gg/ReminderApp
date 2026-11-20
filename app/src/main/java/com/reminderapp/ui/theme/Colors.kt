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
        surfaceVariant = AppSurfaceVariant,
        onSurfaceVariant = AppOnSurfaceVariant,
        surfaceContainerLowest = AppSurfaceContainerLowest,
        surfaceContainerLow = AppSurfaceContainerLow,
        surfaceContainer = AppSurfaceContainer,
        surfaceContainerHigh = AppSurfaceContainerHigh,
        surfaceContainerHighest = AppSurfaceContainerHighest,
        outline = AppOutline,
        outlineVariant = AppOutline,
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
        secondary = AppAccentAIDark,
        onSecondary = AppOnAccentAIDark,
        secondaryContainer = AppAccentAIContainerDark,
        onSecondaryContainer = AppOnAccentAIContainerDark,
        background = AppBackgroundDark,
        onBackground = AppOnSurfaceDark,
        surface = AppSurfaceDark,
        onSurface = AppOnSurfaceDark,
        surfaceVariant = AppSurfaceVariantDark,
        onSurfaceVariant = AppOnSurfaceVariantDark,
        surfaceContainerLowest = AppSurfaceContainerLowestDark,
        surfaceContainerLow = AppSurfaceContainerLowDark,
        surfaceContainer = AppSurfaceContainerDark,
        surfaceContainerHigh = AppSurfaceContainerHighDark,
        surfaceContainerHighest = AppSurfaceContainerHighestDark,
        outline = AppOutlineDark,
        outlineVariant = AppOutlineDark,
        error = AppMissedDark,
        onError = AppOnMissedDark,
        errorContainer = AppMissedContainerDark,
        onErrorContainer = AppOnMissedContainerDark,
    )
}
