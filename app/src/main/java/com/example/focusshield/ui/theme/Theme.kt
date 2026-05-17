package com.example.focusshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ShieldPrimary,
    secondary = ShieldSecondary,
    error = ShieldDanger,
    background = ShieldBackground,
    surface = ShieldSurface,
    surfaceVariant = ShieldSurfaceBright,
    onPrimary = ShieldBackground,
    onSecondary = ShieldBackground,
    onBackground = ShieldText,
    onSurface = ShieldText,
    onSurfaceVariant = ShieldMuted
)

@Composable
fun FocusShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
