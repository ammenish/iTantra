package com.mirage.itantra.speech.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.Priority
import com.mirage.itantra.speech.AudioData
import dagger.hilt.android.qualifiers.ApplicationContext
import android.media.AudioAttributes
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNativeTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    override var isReady: Boolean = false
        private set

    private var originalVolume: Int = -1
    private var hasAudioFocus: Boolean = false
    private var focusRequest: android.media.AudioFocusRequest? = null
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }

    init {
        initTts()
    }

    private fun initTts() {
        try {
            // Explicitly prefer Google TTS (com.google.android.tts) because Samsung's default TTS
            // does not include voices for Indian languages (Marathi, Tamil, Gujarati, Bengali, etc.)
            val googleTtsPkg = "com.google.android.tts"
            val hasGoogleTts = try {
                context.packageManager.getPackageInfo(googleTtsPkg, 0)
                true
            } catch (e: Exception) {
                false
            }

            tts = if (hasGoogleTts) {
                Log.d("NativeTts", "Binding explicitly to Google TTS engine: $googleTtsPkg")
                TextToSpeech(context, this, googleTtsPkg)
            } else {
                Log.d("NativeTts", "Google TTS not found, falling back to system default")
                TextToSpeech(context, this)
            }
        } catch (e: Exception) {
            Log.e("NativeTts", "Error initializing TTS", e)
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("NativeTts", "TTS Engine initialized successfully")
            
            // Set audio attributes to prioritize media output (fixes Samsung earpiece bug)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
            
            // Tune for natural, human-like speech
            // Slightly slower than default (1.0) for clarity over walkie-talkie
            tts?.setSpeechRate(0.92f)
            // Slightly higher pitch for a more natural, warm voice
            tts?.setPitch(1.05f)
            
            isReady = true
        } else {
            Log.e("NativeTts", "TTS Engine initialization failed with status: $status")
            isReady = false
        }
    }

    /**
     * Pre-process text to insert natural pauses at punctuation marks.
     * This makes the TTS output sound more conversational and human.
     */
    private fun humanizeText(text: String): String {
        var processed = text.trim()
        if (processed.isBlank()) return ""

        processed = processed.replace(Regex("\\s+"), " ")
        // Add brief pause after commas
        processed = processed.replace(" ,", ",")
        processed = processed.replace(",", ", ")
        // Add a longer pause after sentences
        processed = processed.replace(" .", ".")
        processed = processed.replace(". ", ".\n")
        processed = processed.replace("! ", "!\n")
        processed = processed.replace("? ", "?\n")
        // Add pause after dashes
        processed = processed.replace(" - ", " ... ")
        processed = processed.replace(" — ", " ... ")
        // Devanagari / Indic danda sentence pauses
        processed = processed.replace("।", "।\n")
        processed = processed.replace("॥", "॥\n")
        return processed
    }

    override suspend fun synthesize(text: String, language: Language, priority: Priority): AudioData {
        if (!isReady || tts == null) {
            Log.w("NativeTts", "TTS Engine not ready, attempting initialization")
            initTts()
            var waited = 0
            while (!isReady && waited < 10) {
                kotlinx.coroutines.delay(100)
                waited++
            }
            if (!isReady || tts == null) {
                Log.e("NativeTts", "TTS Engine still not ready after waiting")
                return AudioData(FloatArray(0), 16000)
            }
        }

        if (priority == Priority.DANGER || priority == Priority.WARNING) {
            // ── Emergency Alert Audio Setup ──
            // Use STREAM_ALARM which:
            //  - Bypasses DND on most devices (alarm category exception)
            //  - Has separate volume from media/ringtone
            //  - Plays even when screen is locked on most devices
            //
            // DOCUMENTED LIMITATION: Some OEM skins (MIUI, OneUI) may still
            // suppress STREAM_ALARM under strict DND. This is a system-level
            // restriction we cannot override without NotificationManager access.

            originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            
            // For DANGER, max volume. For WARNING, keep current volume but still use ALARM channel to bypass DND.
            val targetVolume = if (priority == Priority.DANGER) maxVolume else originalVolume

            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_ALARM,
                targetVolume,
                0 // No UI flag — don't show volume slider during emergency
            )

            // Request exclusive audio focus with ALARM usage
            val alertAudioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            focusRequest = android.media.AudioFocusRequest.Builder(
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
                .setAudioAttributes(alertAudioAttributes)
                .setWillPauseWhenDucked(false)
                .build()

            audioManager.requestAudioFocus(focusRequest!!)
            hasAudioFocus = true

            // Set TTS to use ALARM audio attributes for this utterance
            tts?.setAudioAttributes(alertAudioAttributes)

            // Vibrate to ensure physical notification even if sound fails
            try {
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(android.os.VibratorManager::class.java)
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                }
                // SOS-like vibration pattern: short-short-short long-long-long short-short-short
                val pattern = longArrayOf(0, 200, 100, 200, 100, 200, 300, 500, 200, 500, 200, 500, 300, 200, 100, 200, 100, 200)
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } catch (e: Exception) {
                Log.w("NativeTts", "Vibration failed (non-critical)", e)
            }

            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("NativeTts", "Alert playback started: $utteranceId")
                }
                override fun onDone(utteranceId: String?) { restoreAudio() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { restoreAudio() }

                private fun restoreAudio() {
                    // Restore original ALARM volume
                    if (originalVolume != -1) {
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, originalVolume, 0)
                        originalVolume = -1
                    }
                    if (hasAudioFocus && focusRequest != null) {
                        audioManager.abandonAudioFocusRequest(focusRequest!!)
                        hasAudioFocus = false
                    }
                    // Restore normal TTS audio attributes for subsequent messages
                    val normalAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(normalAttributes)
                    Log.d("NativeTts", "Alert audio state restored")
                }
            })
        }

        // Set the appropriate language locale for ALL supported languages
        val locale = when (language) {
            Language.HINDI -> Locale("hi", "IN")
            Language.ENGLISH -> Locale("en", "IN") // Indian English accent
            Language.TAMIL -> Locale("ta", "IN")
            Language.GUJARATI -> Locale("gu", "IN")
            Language.MARATHI -> Locale("mr", "IN")
            Language.KANNADA -> Locale("kn", "IN")
            Language.TELUGU -> Locale("te", "IN")
            Language.BENGALI -> Locale("bn", "IN")
            Language.MALAYALAM -> Locale("ml", "IN")
            Language.ASSAMESE -> Locale("as", "IN")
            Language.ODIA -> Locale("or", "IN")
        }
        
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("NativeTts", "Language not supported for $locale, trying base language code ${locale.language}")
            val langOnlyResult = tts?.setLanguage(Locale(locale.language))
            if (langOnlyResult == TextToSpeech.LANG_MISSING_DATA || langOnlyResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("NativeTts", "Base language code ${locale.language} not supported, falling back to Hindi/English")
                val fallback = tts?.setLanguage(Locale("hi", "IN"))
                if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("en", "IN"))
                }
            }
        }

        // Select the highest quality neural voice available for this language
        try {
            val availableVoices = tts?.voices
            val bestVoice = availableVoices?.filter { voice ->
                voice.locale.language == locale.language &&
                !voice.isNetworkConnectionRequired
            }?.maxByOrNull { voice ->
                var score = voice.quality * 10
                if (voice.latency == android.speech.tts.Voice.LATENCY_VERY_LOW) score += 5
                score
            }
            if (bestVoice != null) {
                Log.d("NativeTts", "Using neural voice for ${locale.language}: ${bestVoice.name} (quality=${bestVoice.quality})")
                tts?.voice = bestVoice
            }
        } catch (e: Exception) {
            Log.d("NativeTts", "Voice selection skipped", e)
        }

        try {
            val humanizedText = humanizeText(text)
            // Use QUEUE_FLUSH for immediate playback so received voice starts instantly
            tts?.speak(humanizedText, TextToSpeech.QUEUE_FLUSH, null, "MessageId_${System.currentTimeMillis()}")
            Log.d("NativeTts", "TTS speaking message in ${locale.language}: '$humanizedText'")
        } catch (e: Exception) {
            Log.e("NativeTts", "TTS threw exception, attempting to re-initialize", e)
            isReady = false
            initTts()
        }
        
        // Return empty audio data since we are playing directly via native Android API
        return AudioData(FloatArray(0), 16000)
    }

    override suspend fun loadModel(language: Language): Boolean {
        // Native TTS handles its own models based on the setLanguage call
        return true
    }

    override fun stopPlayback() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("NativeTts", "Error stopping TTS playback", e)
        }
    }

    override fun unloadModel() {
        // No-op for Native TTS
    }

    override fun availableLanguages(): List<Language> {
        return Language.initialLanguages()
    }

    override fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("NativeTts", "Error releasing TTS", e)
        } finally {
            tts = null
            isReady = false
        }
    }
}
