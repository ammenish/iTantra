package com.mirage.itantra.domain.usecase

/**
 * Reusable Word Error Rate (WER) calculator.
 *
 * Extracted from [SttBenchmark] so that live pipeline metrics can
 * compute WER on-the-fly without depending on the benchmark harness.
 *
 * WER = (Substitutions + Deletions + Insertions) / ReferenceWords × 100
 *
 * Uses Levenshtein edit distance on word-level tokens. Handles Devanagari
 * and other Indic scripts by splitting on whitespace and punctuation.
 */
object WerCalculator {

    /**
     * Detailed WER breakdown for a single reference–hypothesis pair.
     */
    data class WerResult(
        /** The reference (ground-truth) transcript */
        val referenceText: String,
        /** The hypothesis (STT-recognized) transcript */
        val recognizedText: String,
        /** Number of word substitutions (wrong word) */
        val substitutions: Int,
        /** Number of word deletions (missing word) */
        val deletions: Int,
        /** Number of word insertions (extra word) */
        val insertions: Int,
        /** Total words in the reference transcript */
        val referenceWordCount: Int
    ) {
        /**
         * Word Error Rate as a fraction (0.0–1.0+).
         * WER can exceed 1.0 if insertions dominate.
         */
        val wer: Double
            get() = if (referenceWordCount > 0) {
                (substitutions + deletions + insertions).toDouble() / referenceWordCount
            } else if (recognizedText.isBlank()) 0.0 else 1.0

        /**
         * WER as a percentage (0.0–100.0+).
         */
        val werPercent: Double
            get() = wer * 100.0

        /**
         * Whether the measured WER is within a given target threshold (as percentage).
         */
        fun isWithinTarget(targetWerPercent: Double = 15.0): Boolean =
            werPercent <= targetWerPercent
    }

    /**
     * Calculate detailed WER between a reference transcript and a recognized hypothesis.
     *
     * @param reference The ground-truth text the user intended to say
     * @param hypothesis The text produced by the STT engine
     * @return [WerResult] with full breakdown (S, D, I, word count, WER)
     */
    fun calculate(reference: String, hypothesis: String): WerResult {
        val refWords = tokenize(reference)
        val hypWords = tokenize(hypothesis)
        val n = refWords.size
        val m = hypWords.size

        // Edge cases
        if (n == 0 && m == 0) {
            return WerResult(reference, hypothesis, 0, 0, 0, 0)
        }
        if (n == 0) {
            return WerResult(reference, hypothesis, 0, 0, m, 0)
        }
        if (m == 0) {
            return WerResult(reference, hypothesis, 0, n, 0, n)
        }

        // ── Levenshtein DP table ──
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i  // deletions
        for (j in 0..m) dp[0][j] = j  // insertions

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (refWords[i - 1].equals(hypWords[j - 1], ignoreCase = true)) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,          // deletion
                    dp[i][j - 1] + 1,          // insertion
                    dp[i - 1][j - 1] + cost    // substitution (or match)
                )
            }
        }

        // ── Backtrace to count S, D, I ──
        var i = n
        var j = m
        var subs = 0
        var dels = 0
        var ins = 0

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] +
                        (if (refWords[i - 1].equals(hypWords[j - 1], ignoreCase = true)) 0 else 1) -> {
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
                    // Safety fallback (shouldn't reach here)
                    i = maxOf(0, i - 1); j = maxOf(0, j - 1)
                }
            }
        }

        return WerResult(
            referenceText = reference,
            recognizedText = hypothesis,
            substitutions = subs,
            deletions = dels,
            insertions = ins,
            referenceWordCount = n
        )
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
}
