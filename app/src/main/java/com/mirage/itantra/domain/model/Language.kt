package com.mirage.itantra.domain.model

/**
 * Supported languages for iTantra communication.
 *
 * The language layer is independent of the communication layer.
 * Adding a new language does NOT require changes to networking code.
 *
 * PS-SPECIFIED LANGUAGES (10):
 *   Hindi, English, Gujarati, Marathi, Kannada,
 *   Malayalam, Tamil, Telugu, Odia, Bengali
 *
 * Assamese is supported by the IndicConformer model but is NOT
 * in the ISRO PS 10-language requirement.
 *
 * @property code ISO 639-1 language code
 * @property nativeName Language name in its native script
 * @property displayName English display name
 * @property languageId Compact ID for packet encoding (0-10)
 * @property isPsSpecified Whether this language is in the PS 10-language requirement
 */
enum class Language(
    val code: String,
    val nativeName: String,
    val displayName: String,
    val languageId: Byte,
    val isPsSpecified: Boolean
) {
    HINDI("hi", "\u0939\u093F\u0928\u094D\u0926\u0940", "Hindi", 0, true),
    ENGLISH("en", "English", "English", 1, true),
    TAMIL("ta", "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD", "Tamil", 2, true),
    ASSAMESE("as", "\u0985\u09B8\u09AE\u09C0\u09AF\u09BC\u09BE", "Assamese", 3, false), // NOT in PS 10-language requirement
    GUJARATI("gu", "\u0A97\u0AC1\u0A9C\u0AB0\u0ABE\u0AA4\u0AC0", "Gujarati", 4, true),
    MARATHI("mr", "\u092E\u0930\u093E\u0920\u0940", "Marathi", 5, true),
    KANNADA("kn", "\u0C95\u0CA8\u0CCD\u0CA8\u0CA1", "Kannada", 6, true),
    MALAYALAM("ml", "\u0D2E\u0D32\u0D2F\u0D3E\u0D33\u0D02", "Malayalam", 7, true),
    TELUGU("te", "\u0C24\u0C46\u0C32\u0C41\u0C17\u0C41", "Telugu", 8, true),
    ODIA("or", "\u0B13\u0B21\u0B3C\u0B3F\u0B06", "Odia", 9, true),
    BENGALI("bn", "\u09AC\u09BE\u0982\u09B2\u09BE", "Bengali", 10, true);

    companion object {
        fun fromLanguageId(id: Byte): Language? = entries.find { it.languageId == id }
        fun fromCode(code: String): Language? = entries.find { it.code == code }

        /**
         * The exact 10 languages specified in the ISRO PS requirement.
         * Use this for compliance validation and language matrix testing.
         */
        fun psSpecifiedLanguages(): List<Language> = entries.filter { it.isPsSpecified }

        /** Alias for backward compatibility */
        fun initialLanguages(): List<Language> = psSpecifiedLanguages()

        /** All languages including non-PS ones (e.g., Assamese) */
        fun allLanguages(): List<Language> = entries.toList()
    }
}
