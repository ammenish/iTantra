package com.mirage.itantra.domain.usecase

import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves message history from local storage.
 */
class GetMessageHistoryUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    /** Observe all messages as a reactive flow */
    fun execute(): Flow<List<Message>> = messageRepository.observeMessages()

    /** Observe messages for a specific peer */
    fun forPeer(peerId: String): Flow<List<Message>> =
        messageRepository.observeMessagesForPeer(peerId)
}
