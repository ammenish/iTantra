package com.mirage.itantra.data.packet

import com.mirage.itantra.domain.model.MessageType
import com.mirage.itantra.domain.model.Priority

/**
 * iTantra binary packet representation.
 *
 * This is the wire format for communication between devices.
 * See implementation_plan.md Â§6 for the binary layout specification.
 *
 * Packet Layout (23-byte header + payload + 2-byte CRC):
 * ```
 * [Version:1][Flags:1][LangID:1][SenderID:8][MsgID:4][SeqNo:2]
 * [FragIdx:1][FragCount:1][PayloadLen:2][Payload:N][CRC16:2]
 * ```
 */
data class ITantraPacket(
    val version: Byte = PROTOCOL_VERSION,
    val messageType: MessageType = MessageType.NORMAL,
    val priority: Priority = Priority.NORMAL,
    val isFragmented: Boolean = false,
    val languageId: Byte = 0,
    val senderId: ByteArray = ByteArray(8),
    val messageId: Int = 0,
    val sequenceNumber: Short = 0,
    val fragmentIndex: Byte = 0,
    val fragmentCount: Byte = 1,
    val payload: ByteArray = ByteArray(0),
    val crc16: Short = 0
) {
    companion object {
        const val PROTOCOL_VERSION: Byte = 0x01
        const val HEADER_SIZE = 23
        const val CRC_SIZE = 2
        const val MAX_PAYLOAD_SIZE = 1024 // Default fragment threshold
    }

    /** Total packet size including header, payload, and CRC */
    val totalSize: Int get() = HEADER_SIZE + payload.size + CRC_SIZE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ITantraPacket) return false
        return version == other.version &&
                messageType == other.messageType &&
                messageId == other.messageId &&
                sequenceNumber == other.sequenceNumber &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + messageId
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
