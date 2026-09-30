package com.mirage.itantra.domain.repository

import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.TransportType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for application settings.
 *
 * Backed by DataStore Preferences in the data layer.
 */
interface SettingsRepository {

    /** User-configured device callsign */
    fun observeCallsign(): Flow<String>
    suspend fun setCallsign(callsign: String)

    /** Selected communication language */
    fun observeLanguage(): Flow<Language>
    suspend fun setLanguage(language: Language)

    /** Selected transport mode */
    fun observeTransportType(): Flow<TransportType>
    suspend fun setTransportType(type: TransportType)

    /** Get current callsign synchronously */
    suspend fun getCallsign(): String

    /** Get current language synchronously */
    suspend fun getLanguage(): Language
}
