package com.mirage.itantra.data.ml

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.common.model.RemoteModelManager
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.usecase.ml.TranslationEngine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MLKitTranslationEngine @Inject constructor() : TranslationEngine {

    override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
        if (sourceLanguage == targetLanguage) {
            return text
        }

        val sourceCode = mapLanguageCode(sourceLanguage)
        val targetCode = mapLanguageCode(targetLanguage)

        if (sourceCode == null || targetCode == null) {
            Log.w("MLKitTranslation", "Unsupported translation pair: ${sourceLanguage.code} -> ${targetLanguage.code}")
            return text
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(targetCode)
            .build()
            
        val translator = Translation.getClient(options)

        return try {
            val conditions = DownloadConditions.Builder()
                .build() // Download over any network
            
            // Ensure model is downloaded
            translator.downloadModelIfNeeded(conditions).await()
            
            // Perform translation
            val result = translator.translate(text).await()
            translator.close()
            result
        } catch (e: Exception) {
            Log.e("MLKitTranslation", "Translation failed", e)
            translator.close()
            text
        }
    }

    override suspend fun prefetchModel(language: Language) {
        val targetCode = mapLanguageCode(language) ?: return
        // Just checking English -> Target is enough to pull the target model
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetCode)
            .build()
            
        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            Log.d("MLKitTranslation", "Prefetched model for ${language.name}")
        } catch (e: Exception) {
            Log.e("MLKitTranslation", "Failed to prefetch model for ${language.name}", e)
        } finally {
            translator.close()
        }
    }

    override suspend fun isModelDownloaded(language: Language): Boolean {
        if (language == Language.ENGLISH) return true // English is base, or we assume it's there
        val targetCode = mapLanguageCode(language) ?: return false
        
        return try {
            val modelManager = RemoteModelManager.getInstance()
            val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            models.any { it.language == targetCode }
        } catch (e: Exception) {
            Log.e("MLKitTranslation", "Failed to check if model is downloaded", e)
            false
        }
    }

    private fun mapLanguageCode(language: Language): String? {
        // ML Kit uses standard BCP-47 tags which match our Language.code
        return TranslateLanguage.fromLanguageTag(language.code) ?: language.code
    }
}
