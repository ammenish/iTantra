package com.mirage.itantra.data.packet

import com.mirage.itantra.domain.model.DeliveryStatus
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.model.MessageType
import com.mirage.itantra.domain.model.Priority
import org.json.JSONObject

/**
 * Phase 4 specific serializer for Messages over raw TCP payload.
 * Later phases will replace this with full ITantraPacket binary protocol.
 */
object PacketSerializer {
    fun encode(message: Message): ByteArray {
        val json = JSONObject().apply {
            put("messageId", message.messageId)
            put("senderId", message.senderId)
            put("receiverId", message.receiverId)
            put("language", message.language.name)
            put("messageType", message.messageType.name)
            put("priority", message.priority.name)
            put("timestamp", message.timestamp)
            put("sequenceNumber", message.sequenceNumber)
            put("textPayload", message.textPayload)
        }
        val payloadBytes = json.toString().toByteArray(Charsets.UTF_8)
        
        // Compute CRC32 for data integrity
        val crc32 = java.util.zip.CRC32()
        crc32.update(payloadBytes)
        val checksum = crc32.value

        // Packet format: [4 bytes CRC32] [N bytes JSON Payload]
        val buffer = java.nio.ByteBuffer.allocate(4 + payloadBytes.size)
        buffer.putInt(checksum.toInt())
        buffer.put(payloadBytes)
        
        return buffer.array()
    }

    fun decode(bytes: ByteArray): Message? {
        if (bytes.size < 4) return null
        
        return try {
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val receivedChecksum = buffer.int
            
            val payloadBytes = ByteArray(bytes.size - 4)
            buffer.get(payloadBytes)
            
            // Verify CRC32
            val crc32 = java.util.zip.CRC32()
            crc32.update(payloadBytes)
            val computedChecksum = crc32.value
            
            if (receivedChecksum != computedChecksum.toInt()) {
                android.util.Log.e("PacketSerializer", "CRC Validation failed! Packet corrupted.")
                return null
            }
            
            val str = String(payloadBytes, Charsets.UTF_8)
            val json = JSONObject(str)
            Message(
                messageId = json.getString("messageId"),
                senderId = json.getString("senderId"),
                receiverId = json.getString("receiverId"),
                language = Language.valueOf(json.getString("language")),
                messageType = MessageType.valueOf(json.getString("messageType")),
                priority = Priority.valueOf(json.getString("priority")),
                timestamp = json.getLong("timestamp"),
                sequenceNumber = json.getInt("sequenceNumber"),
                textPayload = json.getString("textPayload"),
                deliveryStatus = DeliveryStatus.PENDING,
                isOutgoing = false
            )
        } catch (e: Exception) {
            android.util.Log.e("PacketSerializer", "Decode failed", e)
            null
        }
    }
}
