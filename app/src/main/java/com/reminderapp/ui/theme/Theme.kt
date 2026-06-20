package com.reminderapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Color Palette ────────────────────────────────────────────────────────────

val SagePrimary = Color(0xFF6B8F71)
val SageSecondary = Color(0xFFA3B18A)
val SageSurface = Color(0xFFF7F8F6)
val SageSuccess = Color(0xFF7BA05B)

val SagePrimaryContainer = Color(0xFFDCE5D8)
val SageOnPrimaryContainer = Color(0xFF1D2A1D)

val SageSecondaryContainer = Color(0xFFE9EDDF)
val SageOnSecondaryContainer = Color(0xFF1E2219)

val SageSurfaceVariant = Color(0xFFE1E4DC)
val SageOnSurfaceVariant = Color(0xFF444841)

val MutedAmber = Color(0xFFD4A017)
val MutedAmberContainer = Color(0xFFFFF8EC)

val MutedGreen = Color(0xFF6B8F71)
val MutedGreenContainer = Color(0xFFF3F8F3)

val ErrorRed = Color(0xFFB3261E)

// ─── Color Schemes ────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = SagePrimary,
    onPrimary = Color.White,
    primaryContainer = SagePrimaryContainer,
    onPrimaryContainer = SageOnPrimaryContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageSecondaryContainer,
    onSecondaryContainer = SageOnSecondaryContainer,
    tertiary = SageSuccess,
    onTertiary = Color.White,
    tertiaryContainer = MutedGreenContainer,
    onTertiaryContainer = SagePrimary,
    error = ErrorRed,
    surface = SageSurface,
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = SageSurfaceVariant,
    onSurfaceVariant = SageOnSurfaceVariant,
    outline = Color(0xFF747970),
    background = SageSurface,
    onBackground = Color(0xFF1A1C19),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB4CDB4), // Lighter Sage for Dark Mode
    onPrimary = Color(0xFF1D351D),
    primaryContainer = Color(0xFF334B33),
    onPrimaryContainer = Color(0xFFD0E9D0),
    secondary = Color(0xFFC1CDAD),
    onSecondary = Color(0xFF2B341D),
    secondaryContainer = Color(0xFF414B33),
    onSecondaryContainer = Color(0xFFDDE9C8),
    tertiary = Color(0xFFB4CDB4),
    onTertiary = Color(0xFF1D351D),
    error = Color(0xFFF2B8B5),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF444841),
    onSurfaceVariant = Color(0xFFC4C8BB),
    outline = Color(0xFF8E9287),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DE),
)

// ─── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun ReminderAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default for Calm Productivity Theme
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
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
