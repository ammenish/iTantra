package com.mirage.itantra.domain.usecase.ml

import com.mirage.itantra.domain.model.Language

/**
 * Interface for offline text translation.
 */
interface TranslationEngine {

    /**
     * Translates text from a source language to a target language.
     * Must be safe to call on a background thread.
     *
     * @param text The text to translate.
     * @param sourceLanguage The original language of the text.
     * @param targetLanguage The desired output language.
     * @return The translated text, or the original text if translation fails.
     */
    suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String

    /**
     * Triggers a background download of the translation model for the given language.
     */
    suspend fun prefetchModel(language: Language)

    /**
     * Checks if the model for the given language is already downloaded.
     */
    suspend fun isModelDownloaded(language: Language): Boolean
}
