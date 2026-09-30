package com.mirage.itantra.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.TransportType
import com.mirage.itantra.domain.repository.SettingsRepository
import com.mirage.itantra.domain.usecase.ml.TranslationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val callsign: String = "MIRAGE-DEVICE",
    val selectedLanguage: Language = Language.HINDI,
    val transportType: TransportType = TransportType.WIFI_DIRECT,
    val availableLanguages: List<Language> = Language.initialLanguages(),
    val isEditingCallsign: Boolean = false,
    val downloadStatus: Map<Language, Boolean> = emptyMap()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val translationEngine: TranslationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
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
        viewModelScope.launch {
            settingsRepository.observeTransportType().collect { type ->
                _uiState.value = _uiState.value.copy(transportType = type)
            }
        }
        
        // Poll for download status
        viewModelScope.launch {
            while(true) {
                val statuses = mutableMapOf<Language, Boolean>()
                for (lang in _uiState.value.availableLanguages) {
                    statuses[lang] = translationEngine.isModelDownloaded(lang)
                }
                _uiState.value = _uiState.value.copy(downloadStatus = statuses)
                kotlinx.coroutines.delay(2000) // update every 2 seconds
            }
        }
    }

    fun updateCallsign(callsign: String) {
        viewModelScope.launch {
            settingsRepository.setCallsign(callsign)
        }
    }

    fun selectLanguage(language: Language) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
        }
        // Pre-fetch the translation model on a detached scope so it doesn't cancel when screen closes
        android.widget.Toast.makeText(context, "Ensuring ${language.name} model is downloaded...", android.widget.Toast.LENGTH_SHORT).show()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            translationEngine.prefetchModel(language)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "${language.name} model is ready!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun selectTransport(type: TransportType) {
        viewModelScope.launch {
            settingsRepository.setTransportType(type)
        }
    }

    fun toggleEditCallsign() {
        _uiState.value = _uiState.value.copy(
            isEditingCallsign = !_uiState.value.isEditingCallsign
        )
    }
}
