package com.mirage.itantra.domain.model

/**
 * Message type classification for iTantra protocol.
 *
 * @property flag 2-bit flag value for packet encoding
 */
enum class MessageType(val flag: Int) {
    /** Standard communication message */
    NORMAL(0),
    /** Time-sensitive or important message */
    URGENT(1),
    /** Emergency alert â€” triggers priority playback, vibration, and visual warning */
    ALERT(2);

    companion object {
        fun fromFlag(flag: Int): MessageType = entries.find { it.flag == flag } ?: NORMAL
    }
}
