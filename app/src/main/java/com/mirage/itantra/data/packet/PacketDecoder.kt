package com.mirage.itantra.data.packet

/**
 * Decodes raw bytes from transport into an [ITantraPacket].
 *
 * Handles:
 * - Binary deserialization
 * - Header field extraction
 * - UTF-8 payload decoding
 *
 * Full implementation in Phase 4.
 */
interface PacketDecoder {
    /**
     * Decode raw bytes into an iTantra packet.
     *
     * @param data Raw bytes received from transport
     * @return Decoded packet, or null if data is malformed
     */
    fun decode(data: ByteArray): ITantraPacket?
}

/**
 * Stub implementation for Phase 1.
 */
class StubPacketDecoder : PacketDecoder {
    override fun decode(data: ByteArray): ITantraPacket? {
        // Phase 1 stub: treat entire data as UTF-8 payload
        return ITantraPacket(payload = data)
    }
}
