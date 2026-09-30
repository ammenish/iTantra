package com.mirage.itantra.data.packet

import com.mirage.itantra.domain.model.Message

/**
 * Encodes a [Message] into one or more [ITantraPacket]s.
 *
 * Handles:
 * - Message â†’ binary packet serialization
 * - UTF-8 text payload encoding
 * - CRC-16/CCITT computation
 * - Packet fragmentation when payload exceeds transport MTU
 *
 * Full implementation in Phase 4.
 */
interface PacketEncoder {
    /**
     * Encode a message into raw bytes ready for transport.
     *
     * @param message The domain message to encode
     * @return List of byte arrays (one per fragment, typically just one)
     */
    fun encode(message: Message): List<ByteArray>
}

/**
 * Stub implementation for Phase 1.
 * Returns the text payload as UTF-8 bytes wrapped in a minimal header.
 */
class StubPacketEncoder : PacketEncoder {
    override fun encode(message: Message): List<ByteArray> {
        // Phase 1 stub: just return UTF-8 text
        return listOf(message.textPayload.toByteArray(Charsets.UTF_8))
    }
}
