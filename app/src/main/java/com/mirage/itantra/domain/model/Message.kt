package com.mirage.itantra.domain.model

import java.util.UUID

/**
 * Core message model for iTantra communication.
 *
 * This is the domain representation of a transmitted/received message.
 * It is independent of the transport layer, packet encoding, and storage layer.
 */
data class Message(
    val messageId: String = UUID.randomUUID().toString().take(8),
    val senderId: String,
    val receiverId: String = "",
    val language: Language,
    val messageType: MessageType = MessageType.NORMAL,
    val priority: Priority = Priority.NORMAL,
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Int = 0,
    val textPayload: String,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val isOutgoing: Boolean = true
)

/**
 * Delivery status of a message.
 */
enum class DeliveryStatus {
    /** Message created, not yet sent */
    PENDING,
    /** Message transmitted to transport */
    SENT,
    /** Message received and validated by peer */
    DELIVERED,
    /** Transmission failed */
    FAILED
}
