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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.ui.theme.*

/**
 * Minimal glassmorphism connection chip.
 */
@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    peerName: String? = null,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> ConnectedGreen
            ConnectionState.DISCONNECTED -> DisconnectedRed
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> ConnectingAmber
            ConnectionState.DISCOVERING -> DiscoveringBlue
            ConnectionState.ERROR -> PrimaryRed
        },
        animationSpec = tween(300),
        label = "status_color"
    )

    val isAlertMode = LocalAlertMode.current
    val bgColor = if (isAlertMode) AlertSurface.copy(alpha = 0.6f) else SurfaceDark.copy(alpha = 0.6f)

    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    val isAnimating = connectionState in listOf(
        ConnectionState.CONNECTING,
        ConnectionState.DISCOVERING,
        ConnectionState.RECONNECTING
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Status dot
        Box(contentAlignment = Alignment.Center) {
            if (isAnimating) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.3f))
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))

        // Status text
        val text = when (connectionState) {
            ConnectionState.CONNECTED -> peerName ?: "CONNECTED"
            ConnectionState.DISCONNECTED -> "DISCONNECTED"
            ConnectionState.CONNECTING -> "CONNECTING..."
            ConnectionState.DISCOVERING -> "DISCOVERING..."
            ConnectionState.RECONNECTING -> "RECONNECTING..."
            ConnectionState.ERROR -> "ERROR"
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isAlertMode) TextPrimaryRed else TextPrimaryGreen,
            fontWeight = FontWeight.SemiBold
        )
    }
}
