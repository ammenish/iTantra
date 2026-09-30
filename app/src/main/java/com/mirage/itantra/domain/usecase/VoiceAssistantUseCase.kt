package com.mirage.itantra.domain.usecase

import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.Priority
import com.mirage.itantra.speech.tts.TextToSpeechEngine
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UseCase for hands-free Voice Assistance.
 * Provides completely audio-based feedback for app events (connected, disconnected, incoming message).
 */
@Singleton
class VoiceAssistantUseCase @Inject constructor(
    private val ttsEngine: TextToSpeechEngine
) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    /**
     * Announces a system event or status to the user.
     * Uses English by default for system prompts, but can be localized later.
     */
    suspend fun announce(prompt: String, isCritical: Boolean = false) = withContext(Dispatchers.Main) {
        // System prompts are usually in the default language (English for now to ensure clarity)
        ttsEngine.synthesize(
            text = prompt,
            language = Language.ENGLISH,
            priority = if (isCritical) Priority.DANGER else Priority.INFO
        )
    }

    suspend fun announceConnected(peerName: String?) {
        val name = peerName ?: "a device"
        announce("Connected to $name")
    }

    suspend fun announceDisconnected() {
        announce("Disconnected")
    }

    suspend fun announceIncomingMessage(senderName: String?, isPriority: Boolean = false) {
        // Removed as per user request
    }

    /** Walkie-talkie "Roger" beep for message received */
    suspend fun playMessageReceivedBeep() = withContext(Dispatchers.Main) {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    /** Walkie-talkie "Over" beep for message sent */
    suspend fun playMessageSentBeep() = withContext(Dispatchers.Main) {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 100)
    }
}
