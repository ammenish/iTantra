package com.mirage.itantra.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.repository.MessageRepository
import com.mirage.itantra.domain.usecase.GetMessageHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mirage.itantra.speech.tts.TextToSpeechEngine
import kotlinx.coroutines.delay

import com.mirage.itantra.domain.repository.SettingsRepository
import com.mirage.itantra.domain.usecase.ml.TranslationEngine

data class HistoryUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getMessageHistoryUseCase: GetMessageHistoryUseCase,
    private val messageRepository: MessageRepository,
    private val ttsEngine: TextToSpeechEngine,
    private val settingsRepository: SettingsRepository,
    private val translationEngine: TranslationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getMessageHistoryUseCase.execute().collect { messages ->
                _uiState.value = HistoryUiState(
                    messages = messages,
                    isLoading = false
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            messageRepository.clearHistory()
        }
    }
    
    private var playbackJob: kotlinx.coroutines.Job? = null

    fun playLast10Messages() {
        if (_uiState.value.isPlaying) {
            // Stop playback
            playbackJob?.cancel()
            playbackJob = null
            ttsEngine.stopPlayback()
            _uiState.value = _uiState.value.copy(isPlaying = false)
            return
        }

        val last10 = _uiState.value.messages.take(10)
        if (last10.isEmpty()) return
        
        _uiState.value = _uiState.value.copy(isPlaying = true)
        
        playbackJob = viewModelScope.launch {
            try {
                val myLanguage = settingsRepository.getLanguage()
                for (message in last10) {
                    val sourceLang = com.mirage.itantra.domain.model.LanguageDetector.detect(message.textPayload, message.language)
                    val textToPlay = if (sourceLang != myLanguage) {
                        translationEngine.translate(message.textPayload, sourceLang, myLanguage)
                    } else {
                        message.textPayload
                    }
                    
                    ttsEngine.synthesize(textToPlay, myLanguage, message.priority)
                    // Small delay to ensure the native TTS queues them up smoothly
                    delay(200)
                }
            } finally {
                // Ensure state is reset when playback finishes or is cancelled
                _uiState.value = _uiState.value.copy(isPlaying = false)
            }
        }
    }
}
