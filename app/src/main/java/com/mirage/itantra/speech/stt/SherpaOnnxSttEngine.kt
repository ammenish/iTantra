package com.mirage.itantra.speech.stt

import android.content.Context
import android.util.Log
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.speech.AudioData
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaOnnxSttEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToTextEngine {

    companion object {
        private const val TAG = "SherpaOnnxSttEngine"
        // 16kHz is expected by IndicConformer models
        private const val SAMPLE_RATE = 16000
    }

    private var recognizer: OfflineRecognizer? = null
    private var currentLanguage: Language? = null

    override val isReady: Boolean
        get() = recognizer != null

    override suspend fun loadModel(language: Language): Boolean = withContext(Dispatchers.IO) {
        if (currentLanguage == language && isReady) {
            return@withContext true
        }
        
        unloadModel()
        
        try {
            val config = OfflineRecognizerConfig(
                featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    nemo = com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig(
                        model = "models/stt/model.int8.onnx"
                    ),
                    tokens = "models/stt/tokens.txt",
                    numThreads = 2,
                    debug = true,
                    modelType = "nemo_ctc"
                )
            )

            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = config
            )
            
            currentLanguage = language
            Log.d(TAG, "Successfully loaded STT model for $language")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load STT model for $language", e)
            false
        }
    }

    override suspend fun transcribe(audio: AudioData, language: Language): String = withContext(Dispatchers.Default) {
        if (!isReady || currentLanguage != language) {
            val loaded = loadModel(language)
            if (!loaded) return@withContext ""
        }
        
        val rec = recognizer ?: return@withContext ""
        
        if (audio.samples.isEmpty()) {
            return@withContext ""
        }
        
        try {
            val startTime = System.currentTimeMillis()
            // Create a stream, accept the waveform, and decode
            val stream = rec.createStream()
            stream.acceptWaveform(audio.samples, audio.sampleRate)
            rec.decode(stream)
            
            val result = rec.getResult(stream)
            stream.release()
            val latency = System.currentTimeMillis() - startTime
            Log.d(TAG, "[Phase 1] STT Latency: ${latency}ms for ${audio.samples.size} samples")
            
            result.text
        } catch (e: Throwable) {
            Log.e(TAG, "Transcription failed", e)
            ""
        }
    }

    override fun unloadModel() {
        recognizer?.release()
        recognizer = null
        currentLanguage = null
    }

    override fun availableLanguages(): List<Language> {
        // AI4Bharat IndicConformer is a multilingual model supporting all 10 PS-specified
        // Indian languages. The same model.int8.onnx handles all languages — language
        // detection is implicit in the CTC decoder output based on the tokens vocabulary.
        //
        // PS-required languages: Hindi, English, Gujarati, Marathi, Kannada,
        // Malayalam, Tamil, Telugu, Odia, Bengali
        return listOf(
            Language.HINDI,
            Language.ENGLISH,
            Language.GUJARATI,
            Language.MARATHI,
            Language.KANNADA,
            Language.MALAYALAM,
            Language.TAMIL,
            Language.TELUGU,
            Language.ODIA,
            Language.BENGALI
        )
    }

    override fun release() {
        unloadModel()
    }
}
