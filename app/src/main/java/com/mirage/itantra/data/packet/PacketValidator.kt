package com.mirage.itantra.data.packet

/**
 * Validates packet integrity using CRC-16/CCITT.
 *
 * Corrupted packets (CRC mismatch) are rejected and never
 * passed to the TTS engine or message processor.
 *
 * Full implementation in Phase 4.
 */
interface PacketValidator {
    /**
     * Validate a packet's CRC-16 checksum.
     *
     * @param packet The decoded packet to validate
     * @return true if CRC matches, false if corrupted
     */
    fun validate(packet: ITantraPacket): Boolean

    /**
     * Validate raw bytes before full decoding.
     *
     * @param data Raw packet bytes including CRC
     * @return true if CRC matches
     */
    fun validateRaw(data: ByteArray): Boolean
}

/**
 * Stub implementation for Phase 1 â€” always passes validation.
 */
class StubPacketValidator : PacketValidator {
    override fun validate(packet: ITantraPacket): Boolean = true
    override fun validateRaw(data: ByteArray): Boolean = true
}
