package com.mirage.itantra.domain.usecase

import android.content.Context
import android.util.Log
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.speech.AudioData
import com.mirage.itantra.speech.stt.SpeechToTextEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * STT Accuracy Benchmark — Word Error Rate (WER) measurement utility.
 *
 * This satisfies the PS Accuracy criterion (40% weight) by providing
 * measured, reproducible WER values for each supported language.
 *
 * METHODOLOGY:
 *   1. Load pre-recorded test audio from assets/benchmark/
 *   2. Run STT inference on each sample
 *   3. Compare output against ground-truth transcriptions
 *   4. Calculate WER = (S + D + I) / N
 *      where S = substitutions, D = deletions, I = insertions, N = reference word count
 *
 * TEST DATA FORMAT:
 *   assets/benchmark/{language_code}/
 *     ├── 01.wav         (16kHz mono PCM WAV)
 *     ├── 01.txt         (ground truth transcription, UTF-8)
 *     ├── 02.wav
 *     ├── 02.txt
 *     └── ...
 *
 * USAGE:
 *   val benchmark = SttBenchmark(context, sttEngine)
 *   val results = benchmark.runFullBenchmark()
 *   results.forEach { Log.i("WER", it.toLogString()) }
 */
@Singleton
class SttBenchmark @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sttEngine: SpeechToTextEngine
) {
    companion object {
        private const val TAG = "SttBenchmark"
        private const val BENCHMARK_DIR = "benchmark"
        private const val SAMPLE_RATE = 16000
    }

    /**
     * Result for a single language benchmark.
     */
    data class LanguageBenchmarkResult(
        val language: Language,
        val totalSentences: Int,
        val totalReferenceWords: Int,
        val totalSubstitutions: Int,
        val totalDeletions: Int,
        val totalInsertions: Int,
        val averageLatencyMs: Long,
        val sentenceResults: List<SentenceResult>
    ) {
        /** Word Error Rate = (S + D + I) / N */
        val wer: Double
            get() = if (totalReferenceWords > 0) {
                (totalSubstitutions + totalDeletions + totalInsertions).toDouble() / totalReferenceWords
            } else 0.0

        /** Word Accuracy = 1 - WER (clamped to 0) */
        val accuracy: Double
            get() = maxOf(0.0, 1.0 - wer)

        fun toLogString(): String = buildString {
            appendLine("═══ STT Benchmark: ${language.displayName} (${language.code}) ═══")
            appendLine("  Sentences Tested:  $totalSentences")
            appendLine("  Reference Words:   $totalReferenceWords")
            appendLine("  Substitutions:     $totalSubstitutions")
            appendLine("  Deletions:         $totalDeletions")
            appendLine("  Insertions:        $totalInsertions")
            appendLine("  WER:               ${"%.2f".format(wer * 100)}%")
            appendLine("  Word Accuracy:     ${"%.2f".format(accuracy * 100)}%")
            appendLine("  Avg Latency:       ${averageLatencyMs}ms")
            appendLine("══════════════════════════════════════════")
        }
    }

    /**
     * Result for a single test sentence.
     */
    data class SentenceResult(
        val fileName: String,
        val referenceText: String,
        val hypothesisText: String,
        val wer: Double,
        val latencyMs: Long,
        val audioDurationMs: Long
    )

    /**
     * Full benchmark result across all languages.
     */
    data class FullBenchmarkResult(
        val languageResults: List<LanguageBenchmarkResult>,
        val overallWer: Double,
        val totalSentences: Int,
        val totalLatencyMs: Long
    ) {
        fun toLogString(): String = buildString {
            appendLine("╔══════════════════════════════════════════╗")
            appendLine("║       iTantra STT Accuracy Report        ║")
            appendLine("╚══════════════════════════════════════════╝")
            appendLine()
            languageResults.forEach { appendLine(it.toLogString()) }
            appendLine("──────────────────────────────────────────")
            appendLine("OVERALL WER:       ${"%.2f".format(overallWer * 100)}%")
            appendLine("OVERALL ACCURACY:  ${"%.2f".format((1.0 - overallWer) * 100)}%")
            appendLine("TOTAL SENTENCES:   $totalSentences")
            appendLine("──────────────────────────────────────────")
        }

        /** Summary table for inclusion in documentation/presentation */
        fun toMarkdownTable(): String = buildString {
            appendLine("| Language | Sentences | WER (%) | Accuracy (%) | Avg Latency (ms) |")
            appendLine("|----------|-----------|---------|--------------|------------------|")
            languageResults.forEach { r ->
                appendLine("| ${r.language.displayName} | ${r.totalSentences} | ${"%.1f".format(r.wer * 100)} | ${"%.1f".format(r.accuracy * 100)} | ${r.averageLatencyMs} |")
            }
            appendLine("| **Overall** | $totalSentences | **${"%.1f".format(overallWer * 100)}** | **${"%.1f".format((1.0 - overallWer) * 100)}** | — |")
        }
    }

    /**
     * Run the full benchmark across all PS-specified languages that have
     * test data available in assets/benchmark/.
     */
    suspend fun runFullBenchmark(): FullBenchmarkResult {
        val results = mutableListOf<LanguageBenchmarkResult>()

        for (language in Language.psSpecifiedLanguages()) {
            val langDir = "$BENCHMARK_DIR/${language.code}"
            if (!hasTestData(langDir)) {
                Log.w(TAG, "No test data for ${language.displayName} at $langDir, skipping")
                continue
            }

            val result = benchmarkLanguage(language, langDir)
            results.add(result)
            Log.i(TAG, result.toLogString())
        }

        val totalRefWords = results.sumOf { it.totalReferenceWords }
        val totalErrors = results.sumOf { it.totalSubstitutions + it.totalDeletions + it.totalInsertions }
        val overallWer = if (totalRefWords > 0) totalErrors.toDouble() / totalRefWords else 0.0

        return FullBenchmarkResult(
            languageResults = results,
            overallWer = overallWer,
            totalSentences = results.sumOf { it.totalSentences },
            totalLatencyMs = results.sumOf { it.averageLatencyMs * it.totalSentences }
        )
    }

    /**
     * Benchmark a single language using its test data directory.
     */
    suspend fun benchmarkLanguage(language: Language, langDir: String): LanguageBenchmarkResult {
        Log.d(TAG, "Benchmarking ${language.displayName}...")

        // Ensure STT model is loaded
        sttEngine.loadModel(language)

        val testFiles = getTestFiles(langDir)
        val sentenceResults = mutableListOf<SentenceResult>()

        for ((audioFile, textFile) in testFiles) {
            try {
                val reference = readTextAsset("$langDir/$textFile").trim()
                val audioData = loadWavAsset("$langDir/$audioFile")

                val startTime = System.currentTimeMillis()
                val hypothesis = sttEngine.transcribe(audioData, language)
                val latencyMs = System.currentTimeMillis() - startTime

                val werResult = calculateWer(reference, hypothesis)

                sentenceResults.add(SentenceResult(
                    fileName = audioFile,
                    referenceText = reference,
                    hypothesisText = hypothesis,
                    wer = werResult,
                    latencyMs = latencyMs,
                    audioDurationMs = audioData.durationMs
                ))

                Log.d(TAG, "  $audioFile: WER=${"%.2f".format(werResult * 100)}%, " +
                        "latency=${latencyMs}ms, ref='${reference.take(30)}', hyp='${hypothesis.take(30)}'")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $audioFile", e)
            }
        }

        val totalRefWords = sentenceResults.sumOf { countWords(it.referenceText) }
        val werDetails = sentenceResults.map { calculateWerDetails(it.referenceText, it.hypothesisText) }

        return LanguageBenchmarkResult(
            language = language,
            totalSentences = sentenceResults.size,
            totalReferenceWords = totalRefWords,
            totalSubstitutions = werDetails.sumOf { it.substitutions },
            totalDeletions = werDetails.sumOf { it.deletions },
            totalInsertions = werDetails.sumOf { it.insertions },
            averageLatencyMs = if (sentenceResults.isNotEmpty())
                sentenceResults.map { it.latencyMs }.average().toLong() else 0L,
            sentenceResults = sentenceResults
        )
    }

    // ── WER Calculation (Levenshtein distance on word sequences) ──

    private data class WerDetails(val substitutions: Int, val deletions: Int, val insertions: Int)

    /**
     * Calculate WER using minimum edit distance (Levenshtein) on word sequences.
     *
     * WER = (S + D + I) / N
     * where:
     *   S = substitutions (wrong word)
     *   D = deletions (missing word)
     *   I = insertions (extra word)
     *   N = number of words in reference
     */
    private fun calculateWer(reference: String, hypothesis: String): Double {
        val details = calculateWerDetails(reference, hypothesis)
        val refWords = countWords(reference)
        return if (refWords > 0) {
            (details.substitutions + details.deletions + details.insertions).toDouble() / refWords
        } else if (hypothesis.isBlank()) 0.0 else 1.0
    }

    private fun calculateWerDetails(reference: String, hypothesis: String): WerDetails {
        val refWords = tokenize(reference)
        val hypWords = tokenize(hypothesis)
        val n = refWords.size
        val m = hypWords.size

        // DP table for edit distance
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i  // deletions
        for (j in 0..m) dp[0][j] = j  // insertions

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (refWords[i - 1].equals(hypWords[j - 1], ignoreCase = true)) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        // Backtrace to count S, D, I
        var i = n
        var j = m
        var subs = 0
        var dels = 0
        var ins = 0

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + (if (refWords[i - 1].equals(hypWords[j - 1], ignoreCase = true)) 0 else 1) -> {
                    if (!refWords[i - 1].equals(hypWords[j - 1], ignoreCase = true)) subs++
                    i--; j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    dels++; i--
                }
                j > 0 && dp[i][j] == dp[i][j - 1] + 1 -> {
                    ins++; j--
                }
                else -> {
                    i--; j--
                }
            }
        }

        return WerDetails(subs, dels, ins)
    }

    /**
     * Tokenize text into words. Handles Devanagari and other Indic scripts
     * by splitting on whitespace and punctuation.
     */
    private fun tokenize(text: String): List<String> {
        return text.trim()
            .replace(Regex("[।,.!?;:\"'()\\[\\]{}]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    private fun countWords(text: String): Int = tokenize(text).size

    // ── Asset Loading ──

    private fun hasTestData(langDir: String): Boolean {
        return try {
            val files = context.assets.list(langDir) ?: emptyArray()
            files.any { it.endsWith(".wav") || it.endsWith(".pcm") }
        } catch (e: Exception) {
            false
        }
    }

    private fun getTestFiles(langDir: String): List<Pair<String, String>> {
        val files = context.assets.list(langDir) ?: emptyArray()
        val audioFiles = files.filter { it.endsWith(".wav") || it.endsWith(".pcm") }.sorted()

        return audioFiles.mapNotNull { audioFile ->
            val baseName = audioFile.substringBeforeLast(".")
            val textFile = "$baseName.txt"
            if (files.contains(textFile)) {
                audioFile to textFile
            } else {
                Log.w(TAG, "No ground truth for $audioFile, skipping")
                null
            }
        }
    }

    private fun readTextAsset(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    /**
     * Load a WAV file from assets and convert to AudioData.
     * Expects 16kHz, 16-bit, mono PCM WAV.
     */
    private fun loadWavAsset(path: String): AudioData {
        val bytes = context.assets.open(path).use { it.readBytes() }

        // Skip WAV header (44 bytes for standard WAV)
        val headerSize = 44
        if (bytes.size <= headerSize) {
            return AudioData.EMPTY
        }

        val pcmBytes = bytes.copyOfRange(headerSize, bytes.size)
        val samples = FloatArray(pcmBytes.size / 2)

        for (i in samples.indices) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val shortVal = (high shl 8 or low).toShort()
            samples[i] = shortVal / 32768.0f
        }

        return AudioData(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            channels = 1
        )
    }
}
