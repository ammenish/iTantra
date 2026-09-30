package com.mirage.itantra.communication.transport

import android.util.Log
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.Peer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A transport decorator that simulates bandwidth-constrained links.
 *
 * This wrapper sits on top of any real Transport implementation (Wi-Fi Direct,
 * Bluetooth, etc.) and artificially throttles data throughput to simulate
 * the conditions of a low-bitrate radio link (e.g., LoRa, UHF, satellite).
 *
 * PURPOSE: To demonstrate that iTantra's semantic packet architecture
 * remains efficient under severely constrained bandwidth — proving the
 * value proposition beyond what a simple Wi-Fi Direct demo can show.
 *
 * USAGE IN DEMO:
 * ```
 * // Wrap the real transport with a simulated 2.4 kbps link (LoRa-like)
 * val throttled = BandwidthThrottledTransport(
 *     delegate = wifiDirectTransport,
 *     targetBitrateBps = 2_400  // 2.4 kbps, typical LoRa
 * )
 * ```
 *
 * BENCHMARK OUTPUT EXAMPLE:
 * ```
 * ═══ Bandwidth Benchmark ═══
 * Link Mode:           Simulated LoRa (2.4 kbps)
 * Raw Audio Equivalent: 160,000 bytes (5s @ 16kHz 16-bit)
 * iTantra Packet:       87 bytes
 * Audio Tx Time:        533.3 seconds (at 2.4 kbps)
 * Packet Tx Time:       0.29 seconds (at 2.4 kbps)
 * Speedup Factor:       1839x
 * ════════════════════════════
 * ```
 *
 * @param delegate The actual transport implementation to wrap
 * @param targetBitrateBps Target bitrate in bits per second
 * @param linkName Human-readable name for logging (e.g., "Simulated LoRa")
 */
class BandwidthThrottledTransport(
    private val delegate: Transport,
    val targetBitrateBps: Int = 2_400,
    val linkName: String = "Simulated Constrained Link"
) : Transport {

    companion object {
        private const val TAG = "ThrottledTransport"

        /** Common constrained link profiles for demonstration */
        const val LORA_BITRATE = 2_400        // LoRa SF7/125kHz effective
        const val LORA_SLOW_BITRATE = 300      // LoRa SF12 long range
        const val UHF_NARROW_BITRATE = 9_600   // UHF narrow-band
        const val SATELLITE_BITRATE = 4_800    // Low-orbit satellite
        const val CODEC2_BITRATE = 1_200       // Codec2 minimum voice
    }

    /** Running total of bytes sent through this transport (for reporting) */
    var totalBytesSent: Long = 0L
        private set

    /** Running total of simulated transmission time */
    var totalSimulatedDelayMs: Long = 0L
        private set

    /** Last send benchmark result */
    var lastBenchmark: BandwidthBenchmark? = null
        private set

    /**
     * Send data through the delegate, but with an artificial delay
     * that simulates the time it would take at the target bitrate.
     */
    override suspend fun send(packet: ByteArray): Result<Unit> {
        val packetBits = packet.size * 8
        val simulatedTransmitTimeMs = (packetBits * 1000L) / targetBitrateBps

        Log.d(TAG, "[$linkName] Sending ${packet.size} bytes at $targetBitrateBps bps → simulated ${simulatedTransmitTimeMs}ms delay")

        // Simulate the constrained link delay
        delay(simulatedTransmitTimeMs)

        // Then actually send through the real transport
        val result = delegate.send(packet)

        totalBytesSent += packet.size
        totalSimulatedDelayMs += simulatedTransmitTimeMs

        // Generate benchmark for this send
        lastBenchmark = BandwidthBenchmark(
            linkName = linkName,
            targetBitrateBps = targetBitrateBps,
            packetSizeBytes = packet.size,
            simulatedTransmitTimeMs = simulatedTransmitTimeMs
        )

        Log.i(TAG, lastBenchmark!!.toLogString())

        return result
    }

    // ── Delegate all other Transport methods unchanged ──

    override suspend fun connect(peer: Peer): Result<Unit> = delegate.connect(peer)
    override suspend fun disconnect() = delegate.disconnect()
    override fun incomingPackets(): Flow<ByteArray> = delegate.incomingPackets()
    override fun connectionState(): StateFlow<ConnectionState> = delegate.connectionState()
    override val connectedPeer: Peer? get() = delegate.connectedPeer
    override suspend fun startDiscovery(): Result<Unit> = delegate.startDiscovery()
    override suspend fun stopDiscovery() = delegate.stopDiscovery()
    override fun discoveredPeers(): Flow<List<Peer>> = delegate.discoveredPeers()
    override fun release() = delegate.release()
}

/**
 * Benchmark result from a bandwidth-constrained transmission.
 * Used for demonstration and PS evaluation.
 */
data class BandwidthBenchmark(
    val linkName: String,
    val targetBitrateBps: Int,
    val packetSizeBytes: Int,
    val simulatedTransmitTimeMs: Long,
    /** Raw audio equivalent (5 seconds of 16kHz 16-bit mono PCM) */
    val rawAudioEquivalentBytes: Int = 5 * 16000 * 2 // 160,000 bytes
) {
    /** Time to send raw audio at the target bitrate, in seconds */
    val rawAudioTransmitTimeSec: Double
        get() = (rawAudioEquivalentBytes * 8.0) / targetBitrateBps

    /** Speedup factor: how much faster semantic packets are vs raw audio */
    val speedupFactor: Double
        get() = if (simulatedTransmitTimeMs > 0) {
            (rawAudioTransmitTimeSec * 1000.0) / simulatedTransmitTimeMs
        } else 0.0

    fun toLogString(): String = buildString {
        appendLine("═══ Bandwidth Benchmark ═══")
        appendLine("Link Mode:              $linkName ($targetBitrateBps bps)")
        appendLine("Raw Audio Equivalent:   $rawAudioEquivalentBytes bytes (5s @ 16kHz 16-bit)")
        appendLine("iTantra Packet:         $packetSizeBytes bytes")
        appendLine("Audio Tx Time:          ${"%.1f".format(rawAudioTransmitTimeSec)} seconds (at $targetBitrateBps bps)")
        appendLine("Packet Tx Time:         ${"%.2f".format(simulatedTransmitTimeMs / 1000.0)} seconds (at $targetBitrateBps bps)")
        appendLine("Speedup Factor:         ${"%.0f".format(speedupFactor)}x")
        appendLine("════════════════════════════")
    }
}
