package com.mirage.itantra.data.repository

import com.mirage.itantra.data.local.MessageDao
import com.mirage.itantra.data.local.MessageEntity
import com.mirage.itantra.domain.model.DeliveryStatus
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.model.MessageType
import com.mirage.itantra.domain.model.Priority
import com.mirage.itantra.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [MessageRepository].
 *
 * Handles mapping between domain [Message] and Room [MessageEntity].
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun observeMessages(): Flow<List<Message>> =
        messageDao.observeAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeMessagesForPeer(peerId: String): Flow<List<Message>> =
        messageDao.observeMessagesForPeer(peerId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getMessageById(messageId: String): Message? =
        messageDao.getMessageById(messageId)?.toDomain()

    override suspend fun saveMessage(message: Message) {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun updateDeliveryStatus(messageId: String, status: DeliveryStatus) {
        messageDao.updateDeliveryStatus(messageId, status.ordinal)
    }

    override suspend fun getMessageCount(): Int = messageDao.getMessageCount()

    override suspend fun clearHistory() = messageDao.clearAll()

    override suspend fun pruneOldMessages(limit: Int) {
        messageDao.deleteMessagesOlderThanLimit(limit)
    }
    // --- Mapping functions ---

    private fun MessageEntity.toDomain(): Message = Message(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        language = Language.fromCode(languageCode) ?: Language.HINDI,
        messageType = MessageType.fromFlag(messageType),
        priority = Priority.fromFlag(priority),
        timestamp = timestamp,
        sequenceNumber = sequenceNumber,
        textPayload = textPayload,
        deliveryStatus = DeliveryStatus.entries.getOrElse(deliveryStatus) { DeliveryStatus.PENDING },
        isOutgoing = isOutgoing
    )

    private fun Message.toEntity(): MessageEntity = MessageEntity(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        languageCode = language.code,
        messageType = messageType.flag,
        priority = priority.flag,
        timestamp = timestamp,
        sequenceNumber = sequenceNumber,
        textPayload = textPayload,
        deliveryStatus = deliveryStatus.ordinal,
        isOutgoing = isOutgoing
    )
}
