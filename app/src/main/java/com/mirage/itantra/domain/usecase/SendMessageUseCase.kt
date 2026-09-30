package com.mirage.itantra.domain.usecase

import android.util.Log
import com.mirage.itantra.domain.model.DeliveryStatus
import com.mirage.itantra.domain.model.Language
import com.mirage.itantra.domain.model.Message
import com.mirage.itantra.domain.model.MessageType
import com.mirage.itantra.domain.model.PipelineMetrics
import com.mirage.itantra.domain.model.Priority
import com.mirage.itantra.domain.repository.MessageRepository
import com.mirage.itantra.domain.repository.SettingsRepository
import com.mirage.itantra.data.packet.PacketSerializer
import com.mirage.itantra.domain.network.P2pTransport
import javax.inject.Inject

/**
 * Orchestrates the send pipeline with granular instrumentation:
 *   Text → Message → PacketEncode → Transport.send()
 *
 * Returns [PipelineMetrics] alongside the message result for
 * PS evaluation criteria (latency, efficiency).
 */
class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val settingsRepository: SettingsRepository,
    private val p2pTransport: P2pTransport
) {
    companion object {
        private const val TAG = "SendPipeline"
    }

    /**
     * Result of the send operation, bundling the message with pipeline metrics.
     */
    data class SendResult(
        val message: Message,
        val metrics: PipelineMetrics
    )

    /**
     * Create and persist an outgoing message, then transmit it.
     *
     * Instruments each stage of the pipeline:
     * - T_packet_encode: PacketSerializer.encode() duration
     * - T_network: p2pTransport.sendPayload() duration
     * - packetSizeBytes: actual encoded payload size
     *
     * STT latency is provided externally by the caller (HomeViewModel)
     * since STT happens before this use case is invoked.
     *
     * @param text The transcribed text to send
     * @param messageType Type classification (NORMAL, URGENT, ALERT)
     * @param priority Priority level for packet ordering
     * @param sttLatencyMs STT processing time (measured by caller)
     * @param audioDurationMs Duration of the captured audio
     * @param rawAudioSizeBytes Size of raw PCM audio in bytes
     * @return Result containing SendResult with metrics on success
     */
    suspend fun execute(
        text: String,
        messageType: MessageType = MessageType.NORMAL,
        priority: Priority = Priority.NORMAL,
        sttLatencyMs: Long = 0L,
        audioDurationMs: Long = 0L,
        rawAudioSizeBytes: Int = 0,
        werResult: WerCalculator.WerResult? = null,
        language: Language? = null
    ): Result<SendResult> {
        val pipelineStartMs = System.currentTimeMillis()

        return try {
            val callsign = settingsRepository.getCallsign()
            val messageLanguage = language ?: settingsRepository.getLanguage()

            val message = Message(
                senderId = callsign,
                language = messageLanguage,
                messageType = messageType,
                priority = priority,
                textPayload = text.trim(),
                deliveryStatus = DeliveryStatus.PENDING,
                isOutgoing = true
            )

            // Save locally as PENDING
            messageRepository.saveMessage(message)

            // ── Stage: Packet Encoding ──
            val encodeStart = System.currentTimeMillis()
            val payload = PacketSerializer.encode(message)
            val packetEncodeLatencyMs = System.currentTimeMillis() - encodeStart
            val packetSizeBytes = payload.size

            Log.d(TAG, "Packet encode: ${packetEncodeLatencyMs}ms, size: ${packetSizeBytes} bytes")

            // ── Stage: Network Transmission ──
            val networkStart = System.currentTimeMillis()
            val success = p2pTransport.sendPayload(payload)
            val networkLatencyMs = System.currentTimeMillis() - networkStart

            Log.d(TAG, "Network transmit: ${networkLatencyMs}ms, success: $success")

            val pipelineEndMs = System.currentTimeMillis()

            // ── Build Metrics ──
            val metrics = PipelineMetrics(
                audioDurationMs = audioDurationMs,
                rawAudioSizeBytes = rawAudioSizeBytes,
                sttLatencyMs = sttLatencyMs,
                packetEncodeLatencyMs = packetEncodeLatencyMs,
                networkLatencyMs = networkLatencyMs,
                packetSizeBytes = packetSizeBytes,
                textPayloadLength = text.trim().length,
                pipelineStartMs = pipelineStartMs,
                pipelineEndMs = pipelineEndMs,
                werResult = werResult
            )

            Log.i(TAG, metrics.toLogString())

            if (success) {
                messageRepository.updateDeliveryStatus(message.messageId, DeliveryStatus.SENT)
                Result.success(
                    SendResult(
                        message = message.copy(deliveryStatus = DeliveryStatus.SENT),
                        metrics = metrics
                    )
                )
            } else {
                messageRepository.updateDeliveryStatus(message.messageId, DeliveryStatus.FAILED)
                // Still return metrics even on send failure (STT + encoding still happened)
                Result.failure(SendPipelineException(
                    "Failed to send packet: No active connection",
                    metrics
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            Result.failure(e)
        }
    }
}

/**
 * Exception that carries pipeline metrics even when transmission fails.
 * This allows the UI to show metrics (STT worked, encoding worked)
 * while indicating the network send failed.
 */
class SendPipelineException(
    message: String,
    val metrics: PipelineMetrics
) : Exception(message)
