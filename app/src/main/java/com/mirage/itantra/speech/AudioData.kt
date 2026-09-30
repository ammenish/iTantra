package com.mirage.itantra.speech

/**
 * Represents a buffer of audio samples.
 *
 * @property samples PCM audio samples as 16-bit shorts converted to float
 * @property sampleRate Sample rate in Hz (typically 16000 for STT)
 * @property channels Number of audio channels (1 = mono)
 * @property durationMs Duration of this audio buffer in milliseconds
 */
data class AudioData(
    val samples: FloatArray,
    val sampleRate: Int = 16000,
    val channels: Int = 1
) {
    val durationMs: Long
        get() = if (sampleRate > 0) (samples.size * 1000L) / sampleRate else 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioData) return false
        return sampleRate == other.sampleRate &&
                channels == other.channels &&
                samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }

    companion object {
        val EMPTY = AudioData(FloatArray(0))
    }
}
