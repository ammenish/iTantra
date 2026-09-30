package com.mirage.itantra.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.DeliveryStatus
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.model.MessageType
import com.mirage.itantra.domain.model.PipelineMetrics
import com.mirage.itantra.domain.model.Priority
import com.mirage.itantra.domain.model.TransmissionState
import com.mirage.itantra.domain.repository.MessageRepository
import com.mirage.itantra.domain.repository.SettingsRepository
import com.mirage.itantra.domain.usecase.SendMessageUseCase
import com.mirage.itantra.domain.usecase.SendPipelineException
import com.mirage.itantra.domain.usecase.ManageCacheUseCase
import com.mirage.itantra.domain.usecase.VoiceAssistantUseCase
import com.mirage.itantra.domain.usecase.WerCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mirage.itantra.speech.SpeechCapture
import com.mirage.itantra.speech.stt.SpeechToTextEngine
import com.mirage.itantra.speech.stt.EnergyVadEngine
import com.mirage.itantra.speech.stt.SpeechRecognitionManager
import com.mirage.itantra.speech.tts.TextToSpeechEngine
import com.mirage.itantra.domain.model.LanguageDetector

import com.mirage.itantra.domain.model.Peer
import com.mirage.itantra.domain.network.P2pTransport
import com.mirage.itantra.domain.usecase.ReceiveMessageUseCase
import com.mirage.itantra.domain.usecase.ml.TranslationEngine
import kotlinx.coroutines.Dispatchers

/**
 * UI state for the main walkie-talkie screen.
 */
data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val availablePeers: List<Peer> = emptyList(),
    val peerName: String? = null,
    val callsign: String = "MIRAGE-DEVICE",
    val selectedLanguage: Language = Language.HINDI,
    val transmissionState: TransmissionState = TransmissionState.IDLE,
    val isPttPressed: Boolean = false,
    val isAutoMode: Boolean = false,
    val isProcessing: Boolean = false,
    val lastReceivedMessage: Message? = null,
    val recentMessages: List<Message> = emptyList(),
    val isAlertDialogVisible: Boolean = false,
    val isAlertMode: Boolean = false,
    val currentTranscription: String = "",
    val currentTranslation: String = "",
    val errorMessage: String? = null,
    // ── Pipeline Metrics (for PS evaluation display) ──
    val sttLatencyMs: Long = 0L,
    val packetSizeBytes: Int = 0,
    val totalLatencyMs: Long = 0L,
    /** Full granular metrics from last send operation */
    val lastSendMetrics: PipelineMetrics? = null,
    /** Full granular metrics from last receive operation */
    val lastReceiveMetrics: PipelineMetrics? = null,
    // ── WER Evaluation ──
    /** Current reference transcript for WER calculation (empty = WER disabled) */
    val referenceTranscript: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val receiveMessageUseCase: ReceiveMessageUseCase,
    private val p2pTransport: P2pTransport,
    private val messageRepository: MessageRepository,
    private val settingsRepository: SettingsRepository,
    private val speechCapture: SpeechCapture,
    private val sttEngine: SpeechToTextEngine,
    private val energyVadEngine: EnergyVadEngine,
    private val manageCacheUseCase: ManageCacheUseCase,
    private val voiceAssistantUseCase: VoiceAssistantUseCase,
    private val translationEngine: TranslationEngine,
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val ttsEngine: TextToSpeechEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private var displayWatermark = System.currentTimeMillis()
    private var timerJob: kotlinx.coroutines.Job? = null
    private var autoModeJob: kotlinx.coroutines.Job? = null


    /** Reference transcript for live WER evaluation. Set by user or loaded from benchmark. */
    private val _referenceTranscript = MutableStateFlow("")

    init {
        observeSettings()
        observeMessages()
        observeNetwork()
        observeReceiverMetrics()
        observeReferenceTranscript()
        receiveMessageUseCase.startListening()
        
        // Automatically ensure models are downloaded on app startup
        viewModelScope.launch(Dispatchers.IO) {
            val supportedLanguages = listOf(Language.ENGLISH, Language.HINDI, Language.MARATHI, Language.TAMIL)
            for (lang in supportedLanguages) {
                translationEngine.prefetchModel(lang)
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            var previousState = _uiState.value.connectionState
            p2pTransport.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
                
                // Voice Assistant Announce
                if (previousState != state) {
                    if (state == ConnectionState.CONNECTED) {
                        voiceAssistantUseCase.announceConnected(_uiState.value.peerName)
                    } else if (state == ConnectionState.DISCONNECTED && previousState == ConnectionState.CONNECTED) {
                        voiceAssistantUseCase.announceDisconnected()
                    }
                    previousState = state
                }
            }
        }
        viewModelScope.launch {
            p2pTransport.peers.collect { peers ->
                _uiState.value = _uiState.value.copy(availablePeers = peers)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.observeCallsign().collect { callsign ->
                _uiState.value = _uiState.value.copy(callsign = callsign)
            }
        }
        viewModelScope.launch {
            settingsRepository.observeLanguage().collect { language ->
                _uiState.value = _uiState.value.copy(selectedLanguage = language)
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages().collect { messages ->
                val newestIncoming = messages.firstOrNull { !it.isOutgoing && it.timestamp > displayWatermark }
                val isNewMessage = newestIncoming != null && newestIncoming.messageId != _uiState.value.lastReceivedMessage?.messageId
                
                _uiState.value = _uiState.value.copy(
                    recentMessages = messages.take(20),
                    // Only show messages received during this app session that haven't been cleared
                    lastReceivedMessage = newestIncoming,
                    // Clear our outgoing transcription so the incoming message text takes priority on screen
                    currentTranscription = if (isNewMessage) "" else _uiState.value.currentTranscription,
                    currentTranslation = if (isNewMessage) "" else _uiState.value.currentTranslation
                )
            }
        }
    }

    /**
     * Observe receiver-side pipeline metrics from ReceiveMessageUseCase.
     * Updates the UI state with the latest receive metrics for display.
     */
    private fun observeReceiverMetrics() {
        viewModelScope.launch {
            receiveMessageUseCase.receiverMetrics.collect { metrics ->
                _uiState.value = _uiState.value.copy(lastReceiveMetrics = metrics)
            }
        }
    }

    /**
     * Observe the reference transcript and sync it to UI state.
     */
    private fun observeReferenceTranscript() {
        viewModelScope.launch {
            _referenceTranscript.collect { ref ->
                _uiState.value = _uiState.value.copy(referenceTranscript = ref)
            }
        }
    }

    // ── PTT Actions ──

    private var speechStartTimeMs = 0L

    fun onPttPressed() {
        if (_uiState.value.isAutoMode) return
        _uiState.value = _uiState.value.copy(
            isPttPressed = true,
            transmissionState = TransmissionState.RECORDING,
            currentTranscription = "",
            currentTranslation = ""
        )
        _recordingDurationMs.value = 0L
        speechStartTimeMs = System.currentTimeMillis()

        timerJob = viewModelScope.launch {
            while (isActive) {
                _recordingDurationMs.value = System.currentTimeMillis() - speechStartTimeMs
                kotlinx.coroutines.delay(100)
            }
        }

        if (speechRecognitionManager.isAvailable()) {
            speechRecognitionManager.startListening(
                language = _uiState.value.selectedLanguage,
                onPartialResult = { partial ->
                    _uiState.value = _uiState.value.copy(currentTranscription = partial)
                },
                onFinalResult = { final ->
                    processSpeechTranscription(final, speechStartTimeMs, isFromAutoMode = false)
                },
                onError = { errorCode, errorMsg ->
                    android.util.Log.w("HomeViewModel", "STT PTT error: $errorMsg ($errorCode)")
                    if (!_uiState.value.isAutoMode && _uiState.value.transmissionState == TransmissionState.RECORDING) {
                        _uiState.value = _uiState.value.copy(
                            transmissionState = TransmissionState.IDLE,
                            isProcessing = false,
                            errorMessage = errorMsg
                        )
                    }
                }
            )
        } else {
            energyVadEngine.reset()
            speechCapture.startCapture()
        }
    }

    fun onPttReleased() {
        if (_uiState.value.isAutoMode) return
        val flowStartTime = speechStartTimeMs.takeIf { it > 0 } ?: System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            isPttPressed = false,
            isProcessing = true,
            transmissionState = TransmissionState.PROCESSING_STT
        )
        timerJob?.cancel()

        if (speechRecognitionManager.isAvailable()) {
            speechRecognitionManager.stopListening()
        } else {
            val audioData = try {
                speechCapture.stopCapture()
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error stopping capture", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    transmissionState = TransmissionState.IDLE,
                    currentTranscription = "[Capture Error]"
                )
                return
            }
            processAudio(audioData, flowStartTime)
        }
    }

    // ── Auto Mode ──

    fun toggleAutoMode() {
        val newState = !_uiState.value.isAutoMode
        _uiState.value = _uiState.value.copy(isAutoMode = newState, isPttPressed = false)
        if (newState) {
            startAutoMode()
        } else {
            stopAutoMode()
        }
    }

    private fun startAutoMode() {
        _uiState.value = _uiState.value.copy(transmissionState = TransmissionState.RECORDING)
        if (speechRecognitionManager.isAvailable()) {
            startNativeAutoSpeechRecognition()
        } else {
            startFallbackVadAutoMode()
        }
    }

    private fun startNativeAutoSpeechRecognition() {
        if (!_uiState.value.isAutoMode) return
        val sessionStart = System.currentTimeMillis()
        speechRecognitionManager.startListening(
            language = _uiState.value.selectedLanguage,
            onPartialResult = { partial ->
                if (_uiState.value.isAutoMode) {
                    _uiState.value = _uiState.value.copy(currentTranscription = partial)
                }
            },
            onFinalResult = { final ->
                if (_uiState.value.isAutoMode) {
                    processSpeechTranscription(final, sessionStart, isFromAutoMode = true)
                }
            },
            onError = { errorCode, errorMsg ->
                android.util.Log.d("HomeViewModel", "Auto Mode STT idle/timeout ($errorCode): $errorMsg")
                // In auto mode, timeout/no-match is expected during silence; resume listening
                if (_uiState.value.isAutoMode) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(200)
                        startNativeAutoSpeechRecognition()
                    }
                }
            }
        )
    }

    private fun startFallbackVadAutoMode() {
        energyVadEngine.reset()
        speechCapture.startCapture()
        autoModeJob = viewModelScope.launch {
            var silenceCounter = 0
            var isSpeaking = false
            var sessionStartTime = System.currentTimeMillis()
            
            speechCapture.audioChunks().collect { chunk ->
                val isSpeech = energyVadEngine.isVoiceActivityDetected(chunk)
                if (isSpeech) {
                    isSpeaking = true
                    silenceCounter = 0
                } else if (isSpeaking) {
                    silenceCounter++
                    if (silenceCounter > 60) {
                        isSpeaking = false
                        silenceCounter = 0
                        val audioData = speechCapture.stopCapture()
                        processAudio(audioData, sessionStartTime)
                        
                        if (_uiState.value.isAutoMode) {
                            kotlinx.coroutines.delay(100)
                            _uiState.value = _uiState.value.copy(transmissionState = TransmissionState.RECORDING)
                            sessionStartTime = System.currentTimeMillis()
                            energyVadEngine.reset()
                            speechCapture.startCapture()
                        }
                    }
                }
            }
        }
    }

    private fun stopAutoMode() {
        if (speechRecognitionManager.isAvailable()) {
            speechRecognitionManager.cancel()
        }
        autoModeJob?.cancel()
        autoModeJob = null
        try { speechCapture.stopCapture() } catch (e: Exception) {}
        _uiState.value = _uiState.value.copy(transmissionState = TransmissionState.IDLE)
    }

    // ── Speech Processing & Bidirectional Translation ──

    private fun processSpeechTranscription(transcription: String, flowStartTime: Long, isFromAutoMode: Boolean) {
        if (transcription.isBlank()) {
            _uiState.value = _uiState.value.copy(
                transmissionState = if (_uiState.value.isAutoMode) TransmissionState.RECORDING else TransmissionState.IDLE,
                isProcessing = false
            )
            if (isFromAutoMode && _uiState.value.isAutoMode) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(200)
                    startNativeAutoSpeechRecognition()
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentTranscription = transcription,
                transmissionState = TransmissionState.PROCESSING_STT,
                isProcessing = true
            )

            val sttLatency = System.currentTimeMillis() - flowStartTime
            val selectedLang = _uiState.value.selectedLanguage

            // 1. Detect actual spoken language from transcript script
            val sourceLang = LanguageDetector.detect(transcription, selectedLang)

            // 2. Bidirectional target language selection
            val targetLang = when {
                sourceLang == selectedLang -> {
                    if (selectedLang == Language.ENGLISH) Language.HINDI else Language.ENGLISH
                }
                sourceLang == Language.ENGLISH -> selectedLang
                else -> selectedLang
            }

            android.util.Log.d("HomeViewModel", "Bidirectional Translation: '$transcription' (${sourceLang.name} → ${targetLang.name})")

            // 3. Translate
            val translationStart = System.currentTimeMillis()
            val translatedText = if (sourceLang != targetLang) {
                try {
                    translationEngine.translate(transcription, sourceLang, targetLang)
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Translation failed", e)
                    ""
                }
            } else ""
            val translationLatency = System.currentTimeMillis() - translationStart
            android.util.Log.d("HomeViewModel", "Translated in ${translationLatency}ms: '$translatedText'")

            // 4. Update UI with both transcription and translation
            _uiState.value = _uiState.value.copy(
                currentTranscription = transcription,
                currentTranslation = translatedText,
                transmissionState = TransmissionState.TRANSMITTING
            )

            // 5. WER calculation if reference transcript is set
            val currentRef = _referenceTranscript.value
            val werResult = if (currentRef.isNotBlank()) {
                WerCalculator.calculate(currentRef, transcription)
            } else null

            val recordedDurationMs = if (flowStartTime > 0) (System.currentTimeMillis() - flowStartTime) else sttLatency
            val rawAudioBytes = (recordedDurationMs * 32).toInt() // 16kHz 16-bit mono = 32 bytes/ms

            // 6. Transmit message
            val result = sendMessageUseCase.execute(
                text = transcription,
                messageType = MessageType.NORMAL,
                priority = Priority.NORMAL,
                sttLatencyMs = sttLatency,
                audioDurationMs = recordedDurationMs,
                rawAudioSizeBytes = rawAudioBytes,
                werResult = werResult,
                language = sourceLang
            )

            val totalLatency = System.currentTimeMillis() - flowStartTime

            result.fold(
                onSuccess = { sendResult ->
                    val metrics = sendResult.metrics
                    _uiState.value = _uiState.value.copy(
                        transmissionState = TransmissionState.TRANSMITTING,
                        sttLatencyMs = sttLatency,
                        packetSizeBytes = metrics.packetSizeBytes,
                        totalLatencyMs = totalLatency,
                        lastSendMetrics = metrics
                    )
                    viewModelScope.launch {
                        voiceAssistantUseCase.playMessageSentBeep()
                    }
                    kotlinx.coroutines.delay(200)
                    if (!_uiState.value.isAutoMode) {
                        _uiState.value = _uiState.value.copy(transmissionState = TransmissionState.IDLE)
                    }
                },
                onFailure = { error ->
                    val metrics = (error as? SendPipelineException)?.metrics
                    _uiState.value = _uiState.value.copy(
                        transmissionState = if (_uiState.value.isAutoMode) TransmissionState.RECORDING else TransmissionState.IDLE,
                        sttLatencyMs = sttLatency,
                        packetSizeBytes = metrics?.packetSizeBytes ?: (transcription.toByteArray(Charsets.UTF_8).size + 200),
                        totalLatencyMs = totalLatency,
                        lastSendMetrics = metrics,
                        errorMessage = "Offline Mode: Transcribed & Translated"
                    )
                }
            )

            _uiState.value = _uiState.value.copy(isProcessing = false)

            // Manage cache footprint
            viewModelScope.launch {
                manageCacheUseCase(maxMessagesToKeep = 50)
            }

            // In AutoMode, resume listening for the next phrase
            if (isFromAutoMode && _uiState.value.isAutoMode) {
                kotlinx.coroutines.delay(250)
                startNativeAutoSpeechRecognition()
            }
        }
    }

    private fun processAudio(audioData: com.mirage.itantra.speech.AudioData, flowStartTime: Long) {
        viewModelScope.launch {
            val rawAudioSizeBytes = audioData.samples.size * 2
            val audioDurationMs = audioData.durationMs

            val sttStart = System.currentTimeMillis()
            val transcription = try {
                sttEngine.transcribe(audioData, _uiState.value.selectedLanguage)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "STT error", e)
                ""
            }
            val sttLatency = System.currentTimeMillis() - sttStart

            if (transcription.isNotBlank()) {
                val selectedLang = _uiState.value.selectedLanguage
                val sourceLang = LanguageDetector.detect(transcription, selectedLang)
                val targetLang = when {
                    sourceLang == selectedLang -> {
                        if (selectedLang == Language.ENGLISH) Language.HINDI else Language.ENGLISH
                    }
                    sourceLang == Language.ENGLISH -> selectedLang
                    else -> selectedLang
                }

                val translatedText = if (sourceLang != targetLang) {
                    try {
                        translationEngine.translate(transcription, sourceLang, targetLang)
                    } catch (e: Exception) {
                        ""
                    }
                } else ""

                val currentRef = _referenceTranscript.value
                val werResult = if (currentRef.isNotBlank()) {
                    WerCalculator.calculate(currentRef, transcription)
                } else null

                val result = sendMessageUseCase.execute(
                    text = transcription,
                    messageType = MessageType.NORMAL,
                    priority = Priority.NORMAL,
                    sttLatencyMs = sttLatency,
                    audioDurationMs = audioDurationMs,
                    rawAudioSizeBytes = rawAudioSizeBytes,
                    werResult = werResult,
                    language = sourceLang
                )
                
                val totalLatency = System.currentTimeMillis() - flowStartTime

                result.fold(
                    onSuccess = { sendResult ->
                        val metrics = sendResult.metrics
                        _uiState.value = _uiState.value.copy(
                            transmissionState = TransmissionState.TRANSMITTING,
                            currentTranscription = transcription,
                            currentTranslation = translatedText,
                            sttLatencyMs = sttLatency,
                            packetSizeBytes = metrics.packetSizeBytes,
                            totalLatencyMs = totalLatency,
                            lastSendMetrics = metrics
                        )
                        viewModelScope.launch {
                            voiceAssistantUseCase.playMessageSentBeep()
                        }
                        kotlinx.coroutines.delay(200)
                        if (!_uiState.value.isAutoMode) {
                            _uiState.value = _uiState.value.copy(transmissionState = TransmissionState.IDLE)
                        }
                    },
                    onFailure = { error ->
                        val metrics = (error as? SendPipelineException)?.metrics
                        _uiState.value = _uiState.value.copy(
                            transmissionState = if (_uiState.value.isAutoMode) TransmissionState.RECORDING else TransmissionState.IDLE,
                            currentTranscription = transcription,
                            currentTranslation = translatedText,
                            sttLatencyMs = sttLatency,
                            packetSizeBytes = metrics?.packetSizeBytes ?: (transcription.toByteArray(Charsets.UTF_8).size + 200),
                            totalLatencyMs = totalLatency,
                            lastSendMetrics = metrics,
                            errorMessage = "Offline Mode: Transcribed & Translated"
                        )
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    currentTranscription = "[No Speech Detected]",
                    currentTranslation = "",
                    transmissionState = if (_uiState.value.isAutoMode) TransmissionState.RECORDING else TransmissionState.IDLE,
                    sttLatencyMs = sttLatency
                )
            }
            
            _uiState.value = _uiState.value.copy(isProcessing = false)

            viewModelScope.launch {
                manageCacheUseCase(maxMessagesToKeep = 50)
            }
        }
    }

    fun clearCurrent() {
        displayWatermark = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            currentTranscription = "",
            currentTranslation = "",
            lastReceivedMessage = null,
            sttLatencyMs = 0L,
            packetSizeBytes = 0,
            totalLatencyMs = 0L,
            lastSendMetrics = null,
            lastReceiveMetrics = null
        )
    }

    // ── WER Reference Transcript Management ──

    /**
     * Set the reference transcript for WER evaluation.
     * When set, each STT result will be compared against this reference
     * to produce a live WER measurement.
     *
     * Pass empty string to disable WER calculation.
     */
    fun setReferenceTranscript(reference: String) {
        _referenceTranscript.value = reference.trim()
    }

    /**
     * Clear the reference transcript, disabling WER calculation.
     */
    fun clearReferenceTranscript() {
        _referenceTranscript.value = ""
    }

    // ── Alert Actions ──

    fun onAlertButtonClicked() {
        _uiState.value = _uiState.value.copy(isAlertDialogVisible = true)
    }

    fun onAlertConfirmed() {
        _uiState.value = _uiState.value.copy(
            isAlertDialogVisible = false,
            isAlertMode = true
        )

        viewModelScope.launch {
            sendMessageUseCase.execute(
                text = "EMERGENCY ALERT — Respond immediately.",
                messageType = MessageType.ALERT,
                priority = Priority.DANGER
            )
        }
    }

    fun onAlertDismissed() {
        _uiState.value = _uiState.value.copy(isAlertDialogVisible = false)
    }

    fun deactivateAlertMode() {
        _uiState.value = _uiState.value.copy(isAlertMode = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ── Network Actions ──
    
    fun onDiscoverPeersClicked() {
        p2pTransport.discoverPeers()
    }
    
    fun onPeerSelected(peer: Peer) {
        p2pTransport.connect(peer)
    }
    
    fun onDisconnectClicked() {
        p2pTransport.disconnect()
    }
    
    override fun onCleared() {
        super.onCleared()
        speechRecognitionManager.destroy()
    }
}
