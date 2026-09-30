package com.mirage.itantra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirage.itantra.domain.model.PipelineMetrics
import com.mirage.itantra.domain.usecase.WerCalculator
import com.mirage.itantra.ui.theme.GlowGreen
import com.mirage.itantra.ui.theme.PrimaryGreen
import com.mirage.itantra.ui.theme.ConnectingAmber

/** Default WER target threshold in percent. WER at or below this is considered "good". */
private const val WER_TARGET_PERCENT = 15.0

/**
 * Collapsible metrics dashboard that displays granular pipeline performance
 * data for PS evaluation.
 *
 * Shows:
 * - Sender metrics: STT, encode, network
 * - Receiver metrics: decode, translate, TTS
 * - Computed values: RTF, compression ratio, total latency
 * - Word Error Rate (WER): live STT accuracy measurement
 *
 * This component is designed to impress evaluators during the demo by
 * showing real, measured performance data.
 */
@Composable
fun MetricsDashboard(
    sendMetrics: PipelineMetrics?,
    receiveMetrics: PipelineMetrics?,
    modifier: Modifier = Modifier,
    referenceTranscript: String = "",
    onReferenceTranscriptChanged: (String) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Don't show if there are no metrics at all
    if (sendMetrics == null && receiveMetrics == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable { isExpanded = !isExpanded }
            .padding(12.dp)
    ) {
        // ── Header Row (always visible) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = "Metrics",
                    tint = PrimaryGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Pipeline Metrics",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Quick summary chips (always visible)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sendMetrics?.let { m ->
                    if (m.sttLatencyMs > 0) {
                        QuickChip("RTF", "${"%.2f".format(m.realTimeFactor)}")
                    }
                    if (m.semanticCompressionRatio > 0) {
                        QuickChip("↓", "${"%.0f".format(m.semanticCompressionRatio)}:1")
                    }
                    // WER quick chip — only when measured data exists
                    m.werResult?.let { wer ->
                        WerQuickChip(wer)
                    }
                }
            }

            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Expanded Detail ──
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .heightIn(max = 240.dp)
                    .verticalScroll(scrollState)
            ) {
                // Sender Metrics
                sendMetrics?.let { m ->
                    MetricsSectionHeader("SENDER")
                    
                    if (m.audioDurationMs > 0) {
                        MetricRow("Audio Duration", "${m.audioDurationMs}ms")
                        MetricRow("Raw Audio Size", "${formatBytes(m.rawAudioSizeBytes)}")
                    }
                    MetricRow(
                        "STT Inference",
                        "${m.sttLatencyMs}ms",
                        highlight = m.sttLatencyMs < m.audioDurationMs
                    )
                    if (m.translationLatencyMs > 0) {
                        MetricRow("Translation", "${m.translationLatencyMs}ms")
                    }
                    MetricRow("Packet Encode", "${m.packetEncodeLatencyMs}ms")
                    MetricRow("Network Tx", "${m.networkLatencyMs}ms")
                    
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    
                    MetricRow(
                        "Total Sender",
                        "${m.totalSenderLatencyMs}ms",
                        isBold = true
                    )
                    MetricRow(
                        "Packet Size",
                        "${m.packetSizeBytes} bytes (${m.textPayloadLength} chars)",
                        isBold = true
                    )

                    // Key computed metrics
                    if (m.realTimeFactor > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ComputedMetricRow(
                            "Real-Time Factor (RTF)",
                            "${"%.3f".format(m.realTimeFactor)}",
                            description = if (m.realTimeFactor < 1.0) "Faster than realtime ✓" else "Slower than realtime",
                            isGood = m.realTimeFactor < 1.0
                        )
                    }
                    if (m.semanticCompressionRatio > 1.0) {
                        ComputedMetricRow(
                            "Semantic Compression",
                            "${"%.0f".format(m.semanticCompressionRatio)}:1",
                            description = "${formatBytes(m.rawAudioSizeBytes)} → ${m.packetSizeBytes} bytes",
                            isGood = true
                        )
                    }

                    // ── Word Error Rate (WER) ──
                    m.werResult?.let { wer ->
                        WerMetricSection(wer)
                    }
                }

                // Receiver Metrics
                receiveMetrics?.let { m ->
                    Spacer(modifier = Modifier.height(12.dp))
                    MetricsSectionHeader("RECEIVER")

                    MetricRow("Packet Decode", "${m.packetDecodeLatencyMs}ms")
                    if (m.receiverTranslationLatencyMs > 0) {
                        MetricRow("Translation", "${m.receiverTranslationLatencyMs}ms")
                    }
                    MetricRow("TTS Synthesis", "${m.ttsLatencyMs}ms")

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    MetricRow(
                        "Total Receiver",
                        "${m.totalReceiverLatencyMs}ms",
                        isBold = true
                    )
                }

                // ── Reference Transcript Editor ──
                Spacer(modifier = Modifier.height(12.dp))
                ReferenceTranscriptEditor(
                    currentReference = referenceTranscript,
                    onReferenceChanged = onReferenceTranscriptChanged
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// WER Display Components
// ════════════════════════════════════════════════════════════════════════

/**
 * Compact WER chip shown in the header alongside RTF and compression ratio.
 * Color-coded: green when within target, amber when above.
 */
@Composable
private fun WerQuickChip(wer: WerCalculator.WerResult) {
    val isGood = wer.isWithinTarget(WER_TARGET_PERCENT)
    val chipBg = if (isGood) PrimaryGreen.copy(alpha = 0.15f) else ConnectingAmber.copy(alpha = 0.15f)
    val labelColor = if (isGood) PrimaryGreen else ConnectingAmber
    val valueColor = if (isGood) GlowGreen else ConnectingAmber

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipBg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            "WER",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            fontSize = 9.sp
        )
        Text(
            "${"%.1f".format(wer.werPercent)}%",
            style = MaterialTheme.typography.labelSmall,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
        if (isGood) {
            Text(
                "✓",
                style = MaterialTheme.typography.labelSmall,
                color = GlowGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }
    }
}

/**
 * WER metric section within the expanded dashboard.
 * Shows the computed WER metric row, and a tappable detail breakdown.
 */
@Composable
private fun WerMetricSection(wer: WerCalculator.WerResult) {
    val isGood = wer.isWithinTarget(WER_TARGET_PERCENT)
    var isDetailExpanded by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(4.dp))

    // Primary WER metric row (styled like RTF and Semantic Compression)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isGood) PrimaryGreen.copy(alpha = 0.08f)
                else ConnectingAmber.copy(alpha = 0.08f)
            )
            .clickable { isDetailExpanded = !isDetailExpanded }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Word Error Rate (WER)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                )
                Text(
                    text = if (isGood) "Within target (≤${WER_TARGET_PERCENT.toInt()}%) ✓"
                           else "Above target (>${WER_TARGET_PERCENT.toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGood) PrimaryGreen else ConnectingAmber,
                    fontSize = 9.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${"%.1f".format(wer.werPercent)}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isGood) GlowGreen else ConnectingAmber,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (isGood) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Within target",
                        tint = GlowGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Icon(
                    if (isDetailExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isDetailExpanded) "Hide details" else "Show details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ── Expandable WER Detail Breakdown ──
        AnimatedVisibility(
            visible = isDetailExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                // Reference text
                WerTranscriptRow(
                    label = "Reference",
                    text = wer.referenceText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Recognized text
                WerTranscriptRow(
                    label = "Recognized",
                    text = wer.recognizedText,
                    color = if (isGood) GlowGreen.copy(alpha = 0.8f) else ConnectingAmber.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Edit distance breakdown
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    WerStatItem("Substitutions", wer.substitutions)
                    WerStatItem("Deletions", wer.deletions)
                    WerStatItem("Insertions", wer.insertions)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // WER formula display
                Text(
                    text = "WER = (${wer.substitutions} + ${wer.deletions} + ${wer.insertions}) / ${wer.referenceWordCount} × 100 = ${"%.1f".format(wer.werPercent)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Shows a labeled transcript row inside the WER detail breakdown.
 */
@Composable
private fun WerTranscriptRow(label: String, text: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PrimaryGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp
        )
        Text(
            text = "\"$text\"",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontStyle = FontStyle.Italic,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/**
 * Single edit distance stat (Substitutions / Deletions / Insertions).
 */
@Composable
private fun WerStatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = if (count == 0) GlowGreen else ConnectingAmber,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp
        )
    }
}

/**
 * Inline editor for setting the reference transcript used for WER calculation.
 * Appears at the bottom of the expanded metrics panel.
 */
@Composable
private fun ReferenceTranscriptEditor(
    currentReference: String,
    onReferenceChanged: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(currentReference) { mutableStateOf(currentReference) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "WER REFERENCE",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryGreen,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 9.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isEditing) {
                    IconButton(
                        onClick = {
                            onReferenceChanged(editText)
                            isEditing = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Save reference",
                            tint = GlowGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            editText = currentReference
                            isEditing = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit reference",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isEditing) {
            BasicTextField(
                value = editText,
                onValueChange = { editText = it },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Default
                ),
                cursorBrush = SolidColor(GlowGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(8.dp),
                singleLine = false,
                maxLines = 3,
                decorationBox = { innerTextField ->
                    Box {
                        if (editText.isEmpty()) {
                            Text(
                                "Type reference phrase (e.g. \"Help me I am trapped\")",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Text(
                text = if (currentReference.isNotBlank()) "\"$currentReference\""
                       else "No reference set — tap edit to enable WER",
                style = MaterialTheme.typography.bodySmall,
                color = if (currentReference.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontStyle = if (currentReference.isBlank()) FontStyle.Italic else FontStyle.Normal,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Existing helper composables (preserved)
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickChip(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PrimaryGreen.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = PrimaryGreen,
            fontSize = 9.sp
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = GlowGreen,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MetricsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = PrimaryGreen,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        fontSize = 10.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    highlight: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) GlowGreen
                    else if (isBold) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ComputedMetricRow(
    label: String,
    value: String,
    description: String,
    isGood: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isGood) PrimaryGreen.copy(alpha = 0.08f)
                else ConnectingAmber.copy(alpha = 0.08f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = if (isGood) PrimaryGreen else ConnectingAmber,
                fontSize = 9.sp
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (isGood) GlowGreen else ConnectingAmber,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatBytes(bytes: Int): String {
    return when {
        bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
        bytes >= 1_000 -> "${"%.1f".format(bytes / 1_000.0)} KB"
        else -> "$bytes B"
    }
}
