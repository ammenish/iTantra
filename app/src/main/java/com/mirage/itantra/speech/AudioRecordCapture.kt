package com.mirage.itantra.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecordCapture @Inject constructor() : SpeechCapture {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNELS = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        
        // Silero VAD prefers 512 samples per chunk (32ms at 16kHz)
        const val CHUNK_SIZE = 512
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    @Volatile
    private var _isCapturing = false
    override val isCapturing: Boolean get() = _isCapturing

    // Buffer to accumulate all captured audio for the current PTT session
    private val fullBuffer = mutableListOf<Float>()

    private val _audioChunks = MutableSharedFlow<FloatArray>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @SuppressLint("MissingPermission") // Handled in UI
    override fun startCapture() {
        if (_isCapturing) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
        // Ensure buffer is large enough
        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE * 2 * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNELS,
            ENCODING,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return
        }

        fullBuffer.clear()
        _isCapturing = true
        audioRecord?.startRecording()

        captureJob = scope.launch {
            val shortBuffer = ShortArray(CHUNK_SIZE)
            
            while (isActive && _isCapturing) {
                val readResult = audioRecord?.read(shortBuffer, 0, CHUNK_SIZE) ?: 0
                if (readResult > 0) {
                    // Convert 16-bit PCM to Float [-1.0, 1.0] for VAD and STT
                    val floatChunk = FloatArray(readResult)
                    for (i in 0 until readResult) {
                        // Normalize 16-bit PCM to float
                        floatChunk[i] = shortBuffer[i] / 32768.0f
                        fullBuffer.add(floatChunk[i])
                    }
                    _audioChunks.tryEmit(floatChunk)
                }
            }
        }
    }

    override fun stopCapture(): AudioData {
        _isCapturing = false
        captureJob?.cancel()
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val finalSamples = fullBuffer.toFloatArray()
        fullBuffer.clear()

        return AudioData(
            samples = finalSamples,
            sampleRate = SAMPLE_RATE,
            channels = 1
        )
    }

    override fun audioChunks(): Flow<FloatArray> = _audioChunks.asSharedFlow()
}
