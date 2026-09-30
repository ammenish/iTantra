package com.mirage.itantra.di

import com.mirage.itantra.speech.AudioRecordCapture
import com.mirage.itantra.speech.SpeechCapture
import com.mirage.itantra.speech.stt.SpeechToTextEngine
import com.mirage.itantra.speech.stt.SherpaOnnxSttEngine
import com.mirage.itantra.speech.tts.TextToSpeechEngine
import com.mirage.itantra.speech.tts.AndroidNativeTtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {

    @Binds
    @Singleton
    abstract fun bindSpeechCapture(
        impl: AudioRecordCapture
    ): SpeechCapture

    // VAD removed for stability

    @Binds
    abstract fun bindSpeechToTextEngine(
        impl: SherpaOnnxSttEngine
    ): SpeechToTextEngine

    @Binds
    @Singleton
    abstract fun bindTextToSpeechEngine(
        impl: AndroidNativeTtsEngine
    ): TextToSpeechEngine
}
