package com.mirage.itantra.communication.transport

import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.Peer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common transport interface for P2P communication.
 *
 * The application layer communicates through this interface
 * rather than directly calling Wi-Fi/Bluetooth APIs.
 *
 * Implementations:
 * - Phase 5: WifiDirectTransport
 * - Phase 9: BluetoothRfcommTransport
 *
 * The STT/TTS pipeline is completely independent of which
 * transport is active. Switching transport does not affect
 * the speech pipeline.
 */
interface Transport {

    /** Connect to a discovered peer */
    suspend fun connect(peer: Peer): Result<Unit>

    /** Disconnect from current peer */
    suspend fun disconnect()

    /** Send raw packet bytes to the connected peer */
    suspend fun send(packet: ByteArray): Result<Unit>

    /** Flow of incoming raw packet bytes from the connected peer */
    fun incomingPackets(): Flow<ByteArray>

    /** Current connection state */
    fun connectionState(): StateFlow<ConnectionState>

    /** Currently connected peer, if any */
    val connectedPeer: Peer?

    /** Start discovering nearby iTantra devices */
    suspend fun startDiscovery(): Result<Unit>

    /** Stop peer discovery */
    suspend fun stopDiscovery()

    /** Flow of discovered peers */
    fun discoveredPeers(): Flow<List<Peer>>

    /** Release all resources */
    fun release()
}
