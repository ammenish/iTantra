package com.mirage.itantra.domain.model

/**
 * Priority levels for message handling and playback ordering.
 *
 * @property level Numeric priority level (higher = more urgent)
 * @property flag 2-bit flag value for packet encoding
 */
enum class Priority(val level: Int, val flag: Int) {
    INFO(0, 0),
    NORMAL(1, 1),
    WARNING(2, 2),
    DANGER(3, 3);

    companion object {
        fun fromFlag(flag: Int): Priority = entries.find { it.flag == flag } ?: NORMAL
    }
}
