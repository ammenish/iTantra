package com.mirage.itantra.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mirage.itantra.ui.theme.*

/**
 * Sleek circular alert button.
 */
@Composable
fun AlertButton(
    isEnabled: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isAlertMode = LocalAlertMode.current

    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_scale"
    )

    val bgColor = if (isEnabled) {
        if (isAlertMode) PrimaryRed.copy(alpha = 0.2f) else SurfaceContainerDark
    } else {
        SurfaceVariantDark
    }

    val iconColor = if (isEnabled) {
        if (isAlertMode) GlowRed else TextSecondaryGreen
    } else {
        TextSecondaryGreen.copy(alpha = 0.3f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .scale(if (isAlertMode) pulseScale else 1f)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = isEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Emergency Alert",
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
