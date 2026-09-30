package com.mirage.itantra.domain.model

/**
 * Detects the language of text based on Unicode script block ranges.
 * Fully offline, zero dependencies, instantaneous execution.
 */
object LanguageDetector {

    /**
     * Determines whether [text] is in Devanagari (Hindi/Marathi), Tamil, Gujarati,
     * Bengali, Telugu, Kannada, Malayalam, Odia, or Latin/English.
     *
     * @param text The input string to inspect.
     * @param fallback The fallback language if no distinct characters are found.
     * @return The detected [Language].
     */
    fun detect(text: String, fallback: Language = Language.ENGLISH): Language {
        if (text.isBlank()) return fallback

        var devanagariCount = 0 // Hindi, Marathi
        var tamilCount = 0
        var gujaratiCount = 0
        var bengaliCount = 0    // Bengali, Assamese
        var teluguCount = 0
        var kannadaCount = 0
        var malayalamCount = 0
        var odiaCount = 0
        var latinCount = 0

        for (ch in text) {
            when (ch) {
                in '\u0900'..'\u097F' -> devanagariCount++
                in '\u0B80'..'\u0BFF' -> tamilCount++
                in '\u0A80'..'\u0AFF' -> gujaratiCount++
                in '\u0980'..'\u09FF' -> bengaliCount++
                in '\u0C00'..'\u0C7F' -> teluguCount++
                in '\u0C80'..'\u0CFF' -> kannadaCount++
                in '\u0D00'..'\u0D7F' -> malayalamCount++
                in '\u0B00'..'\u0B7F' -> odiaCount++
                in 'a'..'z', in 'A'..'Z' -> latinCount++
            }
        }

        val maxIndic = maxOf(
            devanagariCount, tamilCount, gujaratiCount, bengaliCount,
            teluguCount, kannadaCount, malayalamCount, odiaCount
        )

        if (maxIndic == 0 && latinCount == 0) return fallback

        return when {
            tamilCount > 0 && tamilCount == maxIndic -> Language.TAMIL
            devanagariCount > 0 && devanagariCount == maxIndic -> {
                // If the user's active context is Marathi, prioritize Marathi for Devanagari script
                if (fallback == Language.MARATHI) Language.MARATHI else Language.HINDI
            }
            gujaratiCount > 0 && gujaratiCount == maxIndic -> Language.GUJARATI
            bengaliCount > 0 && bengaliCount == maxIndic -> Language.BENGALI
            teluguCount > 0 && teluguCount == maxIndic -> Language.TELUGU
            kannadaCount > 0 && kannadaCount == maxIndic -> Language.KANNADA
            malayalamCount > 0 && malayalamCount == maxIndic -> Language.MALAYALAM
            odiaCount > 0 && odiaCount == maxIndic -> Language.ODIA
            latinCount > maxIndic -> Language.ENGLISH
            else -> fallback
        }
    }
}
