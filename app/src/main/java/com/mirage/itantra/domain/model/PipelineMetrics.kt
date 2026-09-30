package com.mirage.itantra.domain.model

import com.mirage.itantra.domain.usecase.WerCalculator

/**
 * Captures granular latency and efficiency metrics for the entire
 * iTantra communication pipeline.
 *
 * Used to satisfy PS evaluation criteria:
 * - Latency/Speed (20%): T_STT, T_translation, T_packet, T_network, T_TTS, RTF
 * - Efficiency (20%): packetSizeBytes, rawAudioSizeBytes, compressionRatio
 *
 * RTF (Real-Time Factor) = processing_time / audio_duration
 *   RTF < 1.0 means faster-than-realtime processing.
 *
 * Semantic Compression Ratio = rawAudioSizeBytes / packetSizeBytes
 *   Higher is better. A 5-second utterance (160KB PCM) compressed to
 *   a 50-byte text packet yields a ratio of ~3200:1.
 */
data class PipelineMetrics(
    /** Duration of captured audio in milliseconds */
    val audioDurationMs: Long = 0L,

    /** Raw PCM audio size in bytes (samples × 2 for 16-bit) */
    val rawAudioSizeBytes: Int = 0,

    // ── Sender-side latencies ──

    /** Time for STT inference (speech → text) in ms */
    val sttLatencyMs: Long = 0L,

    /** Time for translation (source lang → target lang) in ms */
    val translationLatencyMs: Long = 0L,

    /** Time for packet encoding (Message → ByteArray) in ms */
    val packetEncodeLatencyMs: Long = 0L,

    /** Time for network transmission in ms */
    val networkLatencyMs: Long = 0L,

    // ── Receiver-side latencies ──

    /** Time for packet decoding (ByteArray → Message) in ms */
    val packetDecodeLatencyMs: Long = 0L,

    /** Time for receiver-side translation in ms */
    val receiverTranslationLatencyMs: Long = 0L,

    /** Time for TTS synthesis in ms */
    val ttsLatencyMs: Long = 0L,

    // ── Packet info ──

    /** Size of the transmitted packet in bytes */
    val packetSizeBytes: Int = 0,

    /** Number of UTF-8 characters in the text payload */
    val textPayloadLength: Int = 0,

    // ── Timestamps ──

    /** Wall-clock time when pipeline started (sender) */
    val pipelineStartMs: Long = 0L,

    /** Wall-clock time when pipeline ended (last stage) */
    val pipelineEndMs: Long = 0L,

    // ── STT Accuracy (WER) ──

    /** WER result from comparing STT output against a reference transcript.
     *  Null when no reference transcript is available (normal live usage). */
    val werResult: WerCalculator.WerResult? = null
) {
    /** Total end-to-end latency from speech capture end to send complete */
    val totalSenderLatencyMs: Long
        get() = sttLatencyMs + translationLatencyMs + packetEncodeLatencyMs + networkLatencyMs

    /** Total receiver-side latency from packet receive to TTS start */
    val totalReceiverLatencyMs: Long
        get() = packetDecodeLatencyMs + receiverTranslationLatencyMs + ttsLatencyMs

    /** Full round-trip latency */
    val totalEndToEndLatencyMs: Long
        get() = if (pipelineEndMs > 0 && pipelineStartMs > 0) {
            pipelineEndMs - pipelineStartMs
        } else {
            totalSenderLatencyMs + totalReceiverLatencyMs
        }

    /**
     * Real-Time Factor: processing_time / audio_duration.
     * RTF < 1.0 = faster-than-realtime. Ideal for emergency communication.
     */
    val realTimeFactor: Double
        get() = if (audioDurationMs > 0) {
            sttLatencyMs.toDouble() / audioDurationMs.toDouble()
        } else 0.0

    /**
     * Semantic Compression Ratio: raw_audio_bytes / packet_bytes.
     * Demonstrates the core value proposition of semantic communication.
     *
     * Example: 5s audio at 16kHz 16-bit mono = 160,000 bytes.
     * Semantic text packet ≈ 50-200 bytes → ratio ≈ 800:1 to 3200:1.
     */
    val semanticCompressionRatio: Double
        get() = if (packetSizeBytes > 0) {
            rawAudioSizeBytes.toDouble() / packetSizeBytes.toDouble()
        } else 0.0

    /**
     * Formatted summary for logging and UI display.
     */
    fun toLogString(): String = buildString {
        appendLine("═══ iTantra Pipeline Metrics ═══")
        appendLine("Audio Duration:          ${audioDurationMs}ms")
        appendLine("Raw Audio Size:          ${rawAudioSizeBytes} bytes")
        appendLine("── Sender ──")
        appendLine("  STT Latency:           ${sttLatencyMs}ms")
        appendLine("  Translation Latency:   ${translationLatencyMs}ms")
        appendLine("  Packet Encode:         ${packetEncodeLatencyMs}ms")
        appendLine("  Network Transmit:      ${networkLatencyMs}ms")
        appendLine("  Total Sender:          ${totalSenderLatencyMs}ms")
        appendLine("── Receiver ──")
        appendLine("  Packet Decode:         ${packetDecodeLatencyMs}ms")
        appendLine("  Rx Translation:        ${receiverTranslationLatencyMs}ms")
        appendLine("  TTS Latency:           ${ttsLatencyMs}ms")
        appendLine("  Total Receiver:        ${totalReceiverLatencyMs}ms")
        appendLine("── Summary ──")
        appendLine("  Packet Size:           ${packetSizeBytes} bytes")
        appendLine("  RTF:                   ${"%.3f".format(realTimeFactor)}")
        appendLine("  Compression Ratio:     ${"%.1f".format(semanticCompressionRatio)}:1")
        appendLine("  End-to-End Latency:    ${totalEndToEndLatencyMs}ms")
        werResult?.let { wer ->
            appendLine("── STT Accuracy ──")
            appendLine("  WER:                   ${"%.1f".format(wer.werPercent)}%")
            appendLine("  Reference:             '${wer.referenceText.take(40)}'")
            appendLine("  Recognized:            '${wer.recognizedText.take(40)}'")
            appendLine("  S/D/I:                 ${wer.substitutions}/${wer.deletions}/${wer.insertions}")
        }
        appendLine("════════════════════════════════")
    }
}
