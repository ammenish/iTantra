package com.mirage.itantra.domain.network

import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.Peer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the Peer-to-Peer transport layer for iTantra.
 */
interface P2pTransport {
    
    /** Current state of the P2P connection */
    val connectionState: StateFlow<ConnectionState>
    
    /** List of currently discovered or connected peers */
    val peers: StateFlow<List<Peer>>
    
    /** Flow of incoming raw payloads from the network */
    val incomingPayloads: Flow<ByteArray>

    /**
     * Start discovering nearby peers.
     * Updates [peers] flow with results.
     */
    fun discoverPeers()

    /**
     * Stop the discovery process.
     */
    fun stopDiscovery()

    /**
     * Attempt to connect to a specific peer.
     * @param peer The peer to connect to.
     */
    fun connect(peer: Peer)

    /**
     * Disconnect from the current P2P group.
     */
    fun disconnect()

    /**
     * Send a raw payload to the connected peer(s).
     * @param payload The raw bytes to send.
     * @return true if successfully queued/sent, false otherwise.
     */
    suspend fun sendPayload(payload: ByteArray): Boolean
    
    /**
     * Release all resources.
     */
    fun release()
}
