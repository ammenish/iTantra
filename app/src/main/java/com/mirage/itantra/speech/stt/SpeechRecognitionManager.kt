package com.mirage.itantra.speech.stt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.mirage.itantra.domain.model.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android native SpeechRecognizer for highly accurate multi-lingual speech-to-text.
 *
 * Supports Hindi (hi-IN), Tamil (ta-IN), Marathi (mr-IN), English (en-IN/en-US),
 * and all other Indian languages with native script output (Devanagari, Tamil, etc.).
 */
@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SpeechRecognizerMgr"
        private const val GOOGLE_TTS_PKG = "com.google.android.tts"
        private const val GOOGLE_TTS_RECOGNIZER = "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var isListeningState = false
    val isListening: Boolean get() = isListeningState

    private var onPartialResultCallback: ((String) -> Unit)? = null
    private var onFinalResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((Int, String) -> Unit)? = null
    private var onRmsCallback: ((Float) -> Unit)? = null

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    private fun ensureRecognizer(): SpeechRecognizer {
        speechRecognizer?.let { return it }

        // Use standard system SpeechRecognizer (Google Quick Search Box) which supports
        // all 10 Indian languages seamlessly without strict on-device pack failure (code 13).
        val recognizer = try {
            Log.d(TAG, "Creating standard system SpeechRecognizer")
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create system SpeechRecognizer, falling back to Google component", e)
            val ttsComponent = ComponentName(GOOGLE_TTS_PKG, GOOGLE_TTS_RECOGNIZER)
            SpeechRecognizer.createSpeechRecognizer(context, ttsComponent)
        }
        recognizer.setRecognitionListener(recognitionListener)
        speechRecognizer = recognizer
        return recognizer
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRmsCallback?.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            isListeningState = false
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                13 -> "Language pack unavailable (code 13)"
                else -> "Speech recognition error ($error)"
            }
            Log.w(TAG, "onError: $errorMsg ($error)")

            // Clean up recognizer on critical errors so next run gets a fresh connection
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == SpeechRecognizer.ERROR_CLIENT ||
                error == 13) {
                destroy()
            }

            onErrorCallback?.invoke(error, errorMsg)
        }

        override fun onResults(results: Bundle?) {
            isListeningState = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            val bestMatch = if (matches != null && scores != null && matches.size == scores.size) {
                matches.indices.maxByOrNull { scores[it] }?.let { matches[it] } ?: matches.firstOrNull()
            } else {
                matches?.firstOrNull { it.isNotBlank() }
            } ?: ""

            val cleanedText = normalizeSpeechText(bestMatch)
            Log.d(TAG, "onResults bestMatch: '$cleanedText' (candidates=${matches?.size})")
            onFinalResultCallback?.invoke(cleanedText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull { it.isNotBlank() } ?: ""
            if (text.isNotBlank()) {
                Log.d(TAG, "onPartialResults: '$text'")
                onPartialResultCallback?.invoke(text.trim())
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun normalizeSpeechText(text: String): String {
        var cleaned = text.trim()
        cleaned = cleaned.replace(Regex("^(uh+|um+|ah+|er+)\\s+", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        return cleaned.trim()
    }

    fun startListening(
        language: Language,
        onPartialResult: (String) -> Unit = {},
        onFinalResult: (String) -> Unit,
        onError: (Int, String) -> Unit = { _, _ -> },
        onRmsChanged: (Float) -> Unit = {}
    ) {
        mainHandler.post {
            try {
                if (isListeningState) {
                    cancel()
                }

                this.onPartialResultCallback = onPartialResult
                this.onFinalResultCallback = onFinalResult
                this.onErrorCallback = onError
                this.onRmsCallback = onRmsChanged

                val recognizer = ensureRecognizer()

                val primaryLocaleTag = when (language) {
                    Language.HINDI -> "hi-IN"
                    Language.TAMIL -> "ta-IN"
                    Language.MARATHI -> "mr-IN"
                    Language.GUJARATI -> "gu-IN"
                    Language.BENGALI -> "bn-IN"
                    Language.TELUGU -> "te-IN"
                    Language.KANNADA -> "kn-IN"
                    Language.MALAYALAM -> "ml-IN"
                    Language.ODIA -> "or-IN"
                    Language.ASSAMESE -> "as-IN"
                    Language.ENGLISH -> "en-IN"
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, primaryLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                    // High-accuracy speech endpointing: avoid premature cutoffs when pausing between words
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                }

                isListeningState = true
                recognizer.startListening(intent)
                Log.d(TAG, "Started listening with locale: $primaryLocaleTag")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                isListeningState = false
                destroy()
                onError(-1, e.message ?: "Failed to start recognition")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                if (isListeningState) {
                    speechRecognizer?.stopListening()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognition", e)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                isListeningState = false
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling speech recognition", e)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                isListeningState = false
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speech recognizer", e)
                speechRecognizer = null
            }
        }
    }
}
