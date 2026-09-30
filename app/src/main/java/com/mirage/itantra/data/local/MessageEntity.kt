package com.mirage.itantra.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisted message history.
 *
 * Maps to the domain [com.mirage.itantra.domain.model.Message] model.
 * Stored in the local SQLite database.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val languageCode: String,
    val messageType: Int,
    val priority: Int,
    val timestamp: Long,
    val sequenceNumber: Int,
    val textPayload: String,
    val deliveryStatus: Int,
    val isOutgoing: Boolean
)
