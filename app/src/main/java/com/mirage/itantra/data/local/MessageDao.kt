package com.mirage.itantra.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for message operations.
 */
@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE senderId = :peerId OR receiverId = :peerId ORDER BY timestamp DESC")
    fun observeMessagesForPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: Int)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query("DELETE FROM messages WHERE messageId NOT IN (SELECT messageId FROM messages ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteMessagesOlderThanLimit(limit: Int)
}
