package com.mirage.itantra.data.repository

import com.mirage.itantra.data.local.SettingsDataStore
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.TransportType
import com.mirage.itantra.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [SettingsRepository].
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    override fun observeCallsign(): Flow<String> = settingsDataStore.observeCallsign()

    override suspend fun setCallsign(callsign: String) =
        settingsDataStore.setCallsign(callsign)

    override fun observeLanguage(): Flow<Language> =
        settingsDataStore.observeLanguage().map { code ->
            Language.fromCode(code) ?: Language.HINDI
        }

    override suspend fun setLanguage(language: Language) =
        settingsDataStore.setLanguage(language.code)

    override fun observeTransportType(): Flow<TransportType> =
        settingsDataStore.observeTransportType().map { type ->
            try {
                TransportType.valueOf(type)
            } catch (_: IllegalArgumentException) {
                TransportType.WIFI_DIRECT
            }
        }

    override suspend fun setTransportType(type: TransportType) =
        settingsDataStore.setTransportType(type.name)

    override suspend fun getCallsign(): String = settingsDataStore.getCallsign()

    override suspend fun getLanguage(): Language =
        Language.fromCode(settingsDataStore.getLanguageCode()) ?: Language.HINDI
}
