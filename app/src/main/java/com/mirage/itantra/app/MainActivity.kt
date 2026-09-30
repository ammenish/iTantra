package com.mirage.itantra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mirage.itantra.ui.navigation.NavGraph
import com.mirage.itantra.ui.theme.ITantraTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.mirage.itantra.ui.home.HomeViewModel
import android.media.AudioManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mirage.itantra.service.ITantraService

/**
 * Single-activity host for iTantra.
 *
 * All UI is rendered through Jetpack Compose.
 * Navigation is handled by [NavGraph].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start Foreground Service to keep app alive in background
        val serviceIntent = Intent(this, ITantraService::class.java)
        startForegroundService(serviceIntent)

        // Force speakerphone globally on startup
        forceSpeakerAndMaxVolume()

        setContent {
            val homeViewModel: HomeViewModel = hiltViewModel()
            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

            ITantraTheme(isAlertMode = uiState.isAlertMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        forceSpeakerAndMaxVolume()
    }

    /**
     * Ensures the app always plays audio through the loudspeaker at maximum volume,
     * overriding any earpiece or Bluetooth headset routing if necessary for emergency situations.
     */
    private fun forceSpeakerAndMaxVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Force audio mode to communication and enable speakerphone
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            
            // Maximize STREAM_ALARM (used for alerts and voice assistant)
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
            
            // Maximize STREAM_MUSIC (used for standard walkie-talkie playback)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            
            Log.d("ITantra_Audio", "Forced speakerphone ON and maximized system volumes.")
        } catch (e: Exception) {
            Log.e("ITantra_Audio", "Failed to force speakerphone/volume", e)
        }
    }
}
