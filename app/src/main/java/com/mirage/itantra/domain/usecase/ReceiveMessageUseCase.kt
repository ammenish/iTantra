package com.mirage.itantra.domain.usecase

import android.util.Log
import com.mirage.itantra.data.packet.PacketSerializer
import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.model.PipelineMetrics
import com.mirage.itantra.domain.network.P2pTransport
import com.mirage.itantra.domain.repository.MessageRepository
import com.mirage.itantra.domain.repository.SettingsRepository
import com.mirage.itantra.domain.usecase.ml.TranslationEngine
import com.mirage.itantra.speech.tts.TextToSpeechEngine
import com.mirage.itantra.domain.usecase.VoiceAssistantUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the receive pipeline with granular instrumentation:
 *   Packet → Decode → Translate → Save → TTS
 *
 * Emits [PipelineMetrics] for each received message via [receiverMetrics] flow.
 */
@Singleton
class ReceiveMessageUseCase @Inject constructor(
    private val p2pTransport: P2pTransport,
    private val messageRepository: MessageRepository,
    private val settingsRepository: SettingsRepository,
    private val translationEngine: TranslationEngine,
    private val ttsEngine: TextToSpeechEngine,
    private val voiceAssistantUseCase: VoiceAssistantUseCase
) {
    companion object {
        private const val TAG = "ReceivePipeline"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var collectionJob: Job? = null

    /** Flow of receiver-side metrics for UI display */
    private val _receiverMetrics = MutableSharedFlow<PipelineMetrics>(replay = 1)
    val receiverMetrics: SharedFlow<PipelineMetrics> = _receiverMetrics.asSharedFlow()

    init {
        startListening()
    }
    
    fun startListening() {
        if (collectionJob?.isActive == true) return
        
        collectionJob = scope.launch {
            p2pTransport.incomingPayloads.collect { payload ->
                try {
                    // ── Stage: Packet Decoding ──
                    val decodeStart = System.currentTimeMillis()
                    val message = PacketSerializer.decode(payload)
                    val decodeLatencyMs = System.currentTimeMillis() - decodeStart

                    Log.d(TAG, "Packet decode: ${decodeLatencyMs}ms, payload: ${payload.size} bytes")

                    if (message != null) {
                        execute(message, decodeLatencyMs)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing incoming payload", e)
                }
            }
        }
    }
    
    fun stopListening() {
        collectionJob?.cancel()
        collectionJob = null
    }

    suspend fun execute(message: Message, packetDecodeLatencyMs: Long = 0L): Result<Message> {
        val receiveStartMs = System.currentTimeMillis()

        return try {
            val receivedMessage = message.copy(isOutgoing = false)
            
            // ── Stage: Translation ──
            val myPreferredLanguage = settingsRepository.getLanguage()
            val translationStart = System.currentTimeMillis()
            val detectedSourceLanguage = com.mirage.itantra.domain.model.LanguageDetector.detect(
                receivedMessage.textPayload,
                receivedMessage.language
            )

            var textToSpeak = receivedMessage.textPayload
            var languageToSpeak = receivedMessage.language
            var finalMessage = receivedMessage

            if (detectedSourceLanguage != myPreferredLanguage) {
                try {
                    val translatedText = translationEngine.translate(
                        text = receivedMessage.textPayload,
                        sourceLanguage = detectedSourceLanguage,
                        targetLanguage = myPreferredLanguage
                    )
                    if (translatedText.isNotBlank()) {
                        textToSpeak = translatedText
                        languageToSpeak = myPreferredLanguage
                        finalMessage = receivedMessage.copy(
                            textPayload = translatedText,
                            language = myPreferredLanguage
                        )
                        Log.d(TAG, "Translation succeeded: '$textToSpeak' ($detectedSourceLanguage → $myPreferredLanguage)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Translation failed on receiver, speaking original text in $languageToSpeak: ${e.message}")
                }
            }
            val translationLatencyMs = System.currentTimeMillis() - translationStart

            Log.d(TAG, "Translation stage: ${translationLatencyMs}ms ($detectedSourceLanguage → $myPreferredLanguage)")

            messageRepository.saveMessage(finalMessage)
            
            // ── Stage: TTS Synthesis ──
            val ttsStart = System.currentTimeMillis()
            try {
                // Play incoming transmission chime first to alert the user
                voiceAssistantUseCase.playMessageReceivedBeep()
                kotlinx.coroutines.delay(200)

                // Speak the message out loud through the receiver's speaker
                Log.d(TAG, "TTS synthesizing in $languageToSpeak: '$textToSpeak'")
                ttsEngine.synthesize(textToSpeak, languageToSpeak, finalMessage.priority)
            } catch (e: Exception) {
                Log.e(TAG, "TTS playback failed, message still saved", e)
            }
            val ttsLatencyMs = System.currentTimeMillis() - ttsStart

            Log.d(TAG, "TTS synthesis: ${ttsLatencyMs}ms")

            // ── Build Receiver Metrics ──
            val metrics = PipelineMetrics(
                packetDecodeLatencyMs = packetDecodeLatencyMs,
                receiverTranslationLatencyMs = translationLatencyMs,
                ttsLatencyMs = ttsLatencyMs,
                packetSizeBytes = finalMessage.textPayload.toByteArray(Charsets.UTF_8).size,
                textPayloadLength = finalMessage.textPayload.length,
                pipelineStartMs = receiveStartMs,
                pipelineEndMs = System.currentTimeMillis()
            )

            Log.i(TAG, "── Receiver Metrics ──")
            Log.i(TAG, "  Decode:      ${packetDecodeLatencyMs}ms")
            Log.i(TAG, "  Translation: ${translationLatencyMs}ms")
            Log.i(TAG, "  TTS:         ${ttsLatencyMs}ms")
            Log.i(TAG, "  Total Rx:    ${metrics.totalReceiverLatencyMs}ms")

            _receiverMetrics.emit(metrics)

            Result.success(finalMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
