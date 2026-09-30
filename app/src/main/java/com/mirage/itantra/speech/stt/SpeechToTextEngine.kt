package com.mirage.itantra.speech.stt

import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.speech.AudioData

/**
 * Offline Speech-to-Text engine interface.
 *
 * Implementations run entirely on-device with no Internet dependency.
 * No network request may occur during transcription.
 *
 * Phase 3 provides the IndicConformer / Sherpa-ONNX implementation.
 */
interface SpeechToTextEngine {

    /**
     * Transcribe audio to text offline.
     *
     * @param audio Captured audio data (16kHz mono PCM)
     * @param language Target language for transcription
     * @return Transcribed text string
     */
    suspend fun transcribe(audio: AudioData, language: Language): String

    /** Whether the engine is currently loaded and ready */
    val isReady: Boolean

    /** Load the model for the specified language */
    suspend fun loadModel(language: Language): Boolean

    /** Unload the current model to free memory */
    fun unloadModel()

    /** Get the list of languages with available models */
    fun availableLanguages(): List<Language>

    /** Release all resources */
    fun release()
}
