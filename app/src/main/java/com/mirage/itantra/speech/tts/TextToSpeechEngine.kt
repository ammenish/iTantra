package com.mirage.itantra.speech.tts

import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.Priority
import com.mirage.itantra.speech.AudioData

/**
 * Offline Text-to-Speech engine interface.
 *
 * Implementations run entirely on-device using local model inference.
 * Does NOT use Android cloud TTS for core operation.
 *
 * Phase 7 provides the Piper/VITS + Sherpa-ONNX implementation.
 */
interface TextToSpeechEngine {

    /**
     * Synthesize text to audio offline.
     *
     * @param text Input text to synthesize
     * @param language Language/voice selection
     * @return Synthesized audio data
     */
    suspend fun synthesize(text: String, language: Language, priority: Priority = Priority.NORMAL): AudioData

    /** Whether the engine is currently loaded and ready */
    val isReady: Boolean

    /** Load the voice model for the specified language */
    suspend fun loadModel(language: Language): Boolean

    /** Stop any currently playing audio */
    fun stopPlayback()

    /** Unload the current model to free memory */
    fun unloadModel()

    /** Get the list of languages with available voice models */
    fun availableLanguages(): List<Language>

    /** Release all resources */
    fun release()
}
