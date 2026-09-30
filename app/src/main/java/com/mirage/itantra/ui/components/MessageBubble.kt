package com.mirage.itantra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.model.MessageType
import com.mirage.itantra.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Message bubble for displaying sent/received messages.
 *
 * @param message The message to display
 * @param showTimestamp Whether to show the timestamp
 */
@Composable
fun MessageBubble(
    message: Message,
    showTimestamp: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        message.messageType == MessageType.ALERT -> PrimaryRed.copy(alpha = 0.2f)
        message.isOutgoing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val accentColor = when (message.messageType) {
        MessageType.ALERT -> PrimaryRed
        MessageType.URGENT -> ConnectingAmber
        MessageType.NORMAL -> if (message.isOutgoing) PrimaryGreen else MaterialTheme.colorScheme.onSurface
    }

    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start

    AnimatedVisibility(
        visible = true, // Simplified for this demo, would normally depend on a state
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(300)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = alignment,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
        // Sender label
        Text(
            text = if (message.isOutgoing) "You" else message.senderId,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // Message bubble
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Alert indicator
            if (message.messageType == MessageType.ALERT) {
                Text(
                    text = "🚨 EMERGENCY ALERT",
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Message text
            Text(
                text = message.textPayload,
                style = MaterialTheme.typography.bodyLarge,
                color = accentColor
            )

            // Metadata row
            if (showTimestamp) {
                Row(
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "${message.language.displayName} â€¢ ${formatTime(message.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
