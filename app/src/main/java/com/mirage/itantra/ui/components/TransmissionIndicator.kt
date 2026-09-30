package com.mirage.itantra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.mirage.itantra.domain.model.TransmissionState
import com.mirage.itantra.ui.theme.*

/**
 * Minimal glassmorphism indicator for transmission states.
 */
@Composable
fun TransmissionIndicator(
    state: TransmissionState,
    modifier: Modifier = Modifier
) {
    if (state == TransmissionState.IDLE) return

    val isAlertMode = LocalAlertMode.current

    val stateColor by animateColorAsState(
        targetValue = when (state) {
            TransmissionState.RECORDING -> if (isAlertMode) PrimaryRed else PrimaryGreen
            TransmissionState.PROCESSING_STT, TransmissionState.ENCODING, TransmissionState.PROCESSING_TTS -> ConnectingAmber
            TransmissionState.TRANSMITTING -> if (isAlertMode) PrimaryRed else PrimaryGreen
            TransmissionState.RECEIVING, TransmissionState.PLAYING -> DiscoveringBlue
            TransmissionState.ERROR -> PrimaryRed
            TransmissionState.IDLE -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "tx_color"
    )

    val stateLabel = when (state) {
        TransmissionState.RECORDING -> "RECORDING"
        TransmissionState.PROCESSING_STT -> "TRANSCRIBING"
        TransmissionState.ENCODING -> "ENCODING"
        TransmissionState.TRANSMITTING -> "SENDING"
        TransmissionState.RECEIVING -> "RECEIVING"
        TransmissionState.PROCESSING_TTS -> "SYNTHESIZING"
        TransmissionState.PLAYING -> "PLAYING"
        TransmissionState.ERROR -> "ERROR"
        TransmissionState.IDLE -> ""
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tx_blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    
    val bgColor = if (isAlertMode) AlertSurface.copy(alpha = 0.6f) else SurfaceDark.copy(alpha = 0.6f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(stateColor.copy(alpha = blinkAlpha))
        )

        Text(
            text = stateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = stateColor,
            fontWeight = FontWeight.Bold
        )
    }
}
