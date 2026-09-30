package com.mirage.itantra.data.packet

/**
 * Reassembles fragmented packets into complete messages.
 *
 * Maintains a buffer keyed by (senderId, messageId) to collect
 * fragments and emit complete packets when all fragments arrive.
 * Includes timeout-based cleanup for incomplete fragment sets.
 *
 * Full implementation in Phase 4.
 */
interface PacketReassembler {
    /**
     * Submit a packet fragment for reassembly.
     *
     * @param packet A potentially fragmented packet
     * @return The complete reassembled payload if all fragments received, null otherwise
     */
    fun submit(packet: ITantraPacket): ByteArray?

    /**
     * Clear all pending fragment buffers.
     */
    fun clear()
}

/**
 * Stub implementation for Phase 1 â€” no fragmentation support.
 */
class StubPacketReassembler : PacketReassembler {
    override fun submit(packet: ITantraPacket): ByteArray? = packet.payload
    override fun clear() {}
}
