package com.mirage.itantra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.mirage.itantra.ui.theme.*

/**
 * Premium sleek Push-To-Talk button matching reference UI.
 */
@Composable
fun PTTButton(
    isPressed: Boolean,
    isEnabled: Boolean = true,
    isProcessing: Boolean = false,
    onPressStart: () -> Unit = {},
    onPressEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isAlertMode = LocalAlertMode.current

    val primaryAccent = if (isAlertMode) PrimaryRed else PrimaryGreen
    val glowAccent = if (isAlertMode) GlowRed else GlowGreen

    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Scale animation — shrinks slightly when pressed, pulses when processing
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.90f
            isProcessing -> pulseScale
            else -> 1f
        },
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "ptt_scale"
    )

    // Background color transition
    val bgColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> SurfaceVariantDark
            isPressed || isProcessing -> primaryAccent.copy(alpha = 0.2f)
            else -> SurfaceDark
        },
        animationSpec = tween(200),
        label = "ptt_bg"
    )

    // Border color
    val borderColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> SurfaceVariantDark
            isPressed -> glowAccent
            else -> primaryAccent.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "ptt_border"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> TextSecondaryGreen.copy(alpha = 0.3f)
            isPressed -> glowAccent
            else -> primaryAccent
        },
        animationSpec = tween(200),
        label = "icon_color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Outer glow halo when pressed
        if (isPressed) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(glowAccent.copy(alpha = 0.2f))
            )
        }

        // Main button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .shadow(
                    elevation = if (isPressed) 2.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = if (isPressed) glowAccent else Color.Black,
                    spotColor = if (isPressed) glowAccent else Color.Black
                )
                .clip(CircleShape)
                .background(bgColor)
                .border(2.dp, borderColor, CircleShape)
                .pointerInput(isEnabled) {
                    if (isEnabled) {
                        detectTapGestures(
                            onPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPressStart()
                                tryAwaitRelease()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPressEnd()
                            }
                        )
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Mic",
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
