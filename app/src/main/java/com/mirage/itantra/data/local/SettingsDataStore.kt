package com.mirage.itantra.data.local

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "itantra_settings")

/**
 * DataStore-backed settings persistence.
 *
 * Supports both user-configured callsign and auto-generated device ID as fallback.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_CALLSIGN = stringPreferencesKey("callsign")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_TRANSPORT_TYPE = stringPreferencesKey("transport_type")
    }

    /**
     * Generate a default callsign from device information.
     * Format: MIRAGE-{MODEL}-{LAST4_OF_ANDROID_ID}
     */
    private fun generateDefaultCallsign(): String {
        val model = Build.MODEL.replace(" ", "-").take(8).uppercase()
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeLast(4)?.uppercase() ?: "0000"
        return "MIRAGE-$model-$androidId"
    }

    fun observeCallsign(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CALLSIGN] ?: generateDefaultCallsign()
    }

    suspend fun setCallsign(callsign: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CALLSIGN] = callsign
        }
    }

    suspend fun getCallsign(): String =
        context.dataStore.data.first()[KEY_CALLSIGN] ?: generateDefaultCallsign()

    fun observeLanguage(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "hi" // Default to Hindi
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = languageCode
        }
    }

    suspend fun getLanguageCode(): String =
        context.dataStore.data.first()[KEY_LANGUAGE] ?: "hi"

    fun observeTransportType(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TRANSPORT_TYPE] ?: "WIFI_DIRECT"
    }

    suspend fun setTransportType(type: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TRANSPORT_TYPE] = type
        }
    }
}
