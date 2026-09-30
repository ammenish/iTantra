package com.mirage.itantra.speech

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for microphone audio capture.
 *
 * Implementations use Android [android.media.AudioRecord] to capture
 * mono PCM audio at the required sample rate.
 *
 * Phase 2 provides the real implementation.
 */
interface SpeechCapture {

    /** Start capturing audio from the microphone */
    fun startCapture()

    /** Stop capturing and return the accumulated audio buffer */
    fun stopCapture(): AudioData

    /** Whether capture is currently active */
    val isCapturing: Boolean

    /** Stream of audio chunks as they are captured (for real-time VAD) */
    fun audioChunks(): Flow<FloatArray>
}
