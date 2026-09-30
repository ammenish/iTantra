package com.mirage.itantra.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Local provider for alert state, so components deep in the tree can react
val LocalAlertMode = compositionLocalOf { false }

@Composable
fun ITantraTheme(
    isAlertMode: Boolean = false,
    content: @Composable () -> Unit
) {
    // Animate colors based on alert mode
    val backgroundColor by animateColorAsState(if (isAlertMode) AlertBackground else DarkBackground, tween(500), label = "bg")
    val surfaceColor by animateColorAsState(if (isAlertMode) AlertSurface else SurfaceDark, tween(500), label = "surface")
    val surfaceVariantColor by animateColorAsState(if (isAlertMode) AlertSurfaceVariant else SurfaceVariantDark, tween(500), label = "surfaceVariant")
    val surfaceContainerColor by animateColorAsState(if (isAlertMode) AlertSurfaceContainer else SurfaceContainerDark, tween(500), label = "surfaceContainer")
    
    val primaryColor by animateColorAsState(if (isAlertMode) PrimaryRed else PrimaryGreen, tween(500), label = "primary")
    val onSurfaceColor by animateColorAsState(if (isAlertMode) TextPrimaryRed else TextPrimaryGreen, tween(500), label = "onSurface")
    val onSurfaceVariantColor by animateColorAsState(if (isAlertMode) TextSecondaryRed else TextSecondaryGreen, tween(500), label = "onSurfaceVariant")

    val dynamicColorScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = OnPrimary,
        surface = surfaceColor,
        surfaceVariant = surfaceVariantColor,
        surfaceContainer = surfaceContainerColor,
        surfaceContainerHigh = surfaceContainerColor, // Simplified for now
        onSurface = onSurfaceColor,
        onSurfaceVariant = onSurfaceVariantColor,
        background = backgroundColor,
        onBackground = onSurfaceColor,
        error = PrimaryRed,
        tertiary = ConnectedGreen,
        secondary = ConnectingAmber,
        outline = PTTBorder
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalAlertMode provides isAlertMode) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = ITantraTypography,
            content = content
        )
    }
}
