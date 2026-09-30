package com.mirage.itantra.domain.model

/**
 * Connection state of the P2P transport layer.
 */
enum class ConnectionState {
    /** No active connection or discovery */
    DISCONNECTED,
    /** Actively searching for peers */
    DISCOVERING,
    /** Connection negotiation in progress */
    CONNECTING,
    /** P2P link established, ready for communication */
    CONNECTED,
    /** Connection lost, attempting to restore */
    RECONNECTING,
    /** Transport error requiring user intervention */
    ERROR
}
