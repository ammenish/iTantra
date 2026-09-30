package com.mirage.itantra.domain.repository

import com.mirage.itantra.domain.model.DeliveryStatus
import com.mirage.itantra.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for message persistence.
 *
 * Implementations handle Room database operations.
 * The domain layer depends on this interface, not on Room directly.
 */
interface MessageRepository {

    /** Observe all messages ordered by timestamp descending */
    fun observeMessages(): Flow<List<Message>>

    /** Observe messages for a specific peer */
    fun observeMessagesForPeer(peerId: String): Flow<List<Message>>

    /** Get a single message by ID */
    suspend fun getMessageById(messageId: String): Message?

    /** Save a new message (sent or received) */
    suspend fun saveMessage(message: Message)

    /** Update delivery status of a message */
    suspend fun updateDeliveryStatus(messageId: String, status: DeliveryStatus)

    /** Get count of all stored messages */
    suspend fun getMessageCount(): Int

    /** Delete all message history */
    suspend fun clearHistory()

    /** Delete messages exceeding the specified limit to save space */
    suspend fun pruneOldMessages(limit: Int)
}
