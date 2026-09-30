package com.mirage.itantra.speech.stt

import android.content.Context
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Voice Activity Detection using RMS energy thresholding.
 *
 * This is a lightweight, native-math VAD that avoids ONNX runtime
 * library conflicts with the STT engine. It replaces the original
 * Silero ONNX VAD for stability.
 *
 * Detection approach:
 *  1. Calculates RMS energy of the audio frame
 *  2. Compares against an adaptive threshold
 *  3. Applies minimum speech duration and debounce logic
 *
 * Limitations (be transparent in documentation):
 *  - Less accurate than neural VAD (Silero/WebRTC)
 *  - Can false-trigger on loud non-speech sounds
 *  - Threshold may need tuning per environment
 *
 * @see SherpaOnnxSttEngine for STT inference
 */
@Singleton
class EnergyVadEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EnergyVAD"

        /**
         * Default RMS threshold for float PCM [-1.0, 1.0].
         * 0.02f is a baseline for detecting speech vs silence in quiet environments.
         * Increase for noisy environments, decrease for whispered speech.
         */
        private const val DEFAULT_THRESHOLD = 0.02f

        /**
         * Minimum number of consecutive speech frames required to trigger detection.
         * Prevents single-frame noise spikes from being detected as speech.
         * At 32ms frames: 3 frames = ~96ms minimum utterance.
         */
        private const val MIN_SPEECH_FRAMES = 3

        /**
         * Number of recent frames to use for adaptive noise floor estimation.
         */
        private const val NOISE_FLOOR_WINDOW = 50

        /**
         * Multiplier above noise floor to set adaptive threshold.
         * threshold = noiseFloor * ADAPTIVE_MULTIPLIER
         */
        private const val ADAPTIVE_MULTIPLIER = 3.0f
    }

    /** Configurable speech detection threshold */
    var threshold: Float = DEFAULT_THRESHOLD
        private set

    /** Consecutive speech frame counter for debounce */
    private var speechFrameCount = 0

    /** Recent RMS values for adaptive noise floor */
    private val recentRmsValues = ArrayDeque<Float>(NOISE_FLOOR_WINDOW)

    /** Estimated noise floor (running average of quiet frames) */
    private var noiseFloor: Float = 0.005f

    /** Whether adaptive thresholding is enabled */
    var isAdaptiveMode: Boolean = true

    /**
     * Checks if the float array contains voice activity using RMS energy.
     *
     * @param samples PCM audio samples in [-1.0, 1.0] range
     * @return true if speech is likely detected
     */
    fun isVoiceActivityDetected(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return false

        val rms = calculateRms(samples)

        // Update noise floor with quiet frames
        updateNoiseFloor(rms)

        // Use adaptive threshold if enabled, otherwise use static threshold
        val effectiveThreshold = if (isAdaptiveMode) {
            maxOf(noiseFloor * ADAPTIVE_MULTIPLIER, DEFAULT_THRESHOLD)
        } else {
            threshold
        }

        val isSpeech = rms > effectiveThreshold

        // Debounce: require minimum consecutive speech frames
        if (isSpeech) {
            speechFrameCount++
        } else {
            speechFrameCount = 0
        }

        return speechFrameCount >= MIN_SPEECH_FRAMES
    }

    /**
     * Calculate RMS (Root Mean Square) energy of audio samples.
     */
    private fun calculateRms(samples: FloatArray): Float {
        var sumSquare = 0.0
        for (sample in samples) {
            sumSquare += sample * sample
        }
        return Math.sqrt(sumSquare / samples.size).toFloat()
    }

    /**
     * Update the noise floor estimate using quiet frames.
     * Only updates when the current RMS is below the speech threshold,
     * to avoid contaminating the noise estimate with speech.
     */
    private fun updateNoiseFloor(rms: Float) {
        // Only track frames that are likely noise (below current threshold)
        if (rms < threshold * 1.5f) {
            if (recentRmsValues.size >= NOISE_FLOOR_WINDOW) {
                recentRmsValues.removeFirst()
            }
            recentRmsValues.addLast(rms)

            // Update noise floor as average of recent quiet frames
            if (recentRmsValues.size >= 10) {
                noiseFloor = recentRmsValues.average().toFloat()
            }
        }
    }

    /**
     * Reset internal state. Call when starting a new recording session.
     */
    fun reset() {
        speechFrameCount = 0
        recentRmsValues.clear()
        noiseFloor = 0.005f
    }

    /**
     * Set a manual threshold (disables adaptive mode).
     */
    fun setManualThreshold(value: Float) {
        threshold = value
        isAdaptiveMode = false
    }

    /**
     * Enable adaptive threshold based on ambient noise.
     */
    fun enableAdaptiveMode() {
        isAdaptiveMode = true
    }
}
