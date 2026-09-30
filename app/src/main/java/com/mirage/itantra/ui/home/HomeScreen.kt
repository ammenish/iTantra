package com.mirage.itantra.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.ui.components.*
import com.mirage.itantra.ui.theme.*

import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToConnection: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val audioPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val context = LocalContext.current

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    AnimatedBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onNavigateToConnection) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = "Record",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            ConnectionStatusBar(
                connectionState = state.connectionState,
                peerName = state.peerName
            )
            
            Spacer(modifier = Modifier.weight(1f))

            // Center Area - Waveform & Transcription
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Background waveform ring
                WaveformRenderer(
                    amplitude = if (state.isPttPressed) 0.8f else 0.2f,
                    isCircular = true,
                    modifier = Modifier.fillMaxSize(1.2f)
                )

                // Text Content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    AnimatedContent(
                        targetState = state.currentTranscription.ifBlank { state.lastReceivedMessage?.textPayload ?: "Hold to speak..." },
                        label = "transcription"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = if (state.currentTranslation.isNotBlank()) 8.dp else 16.dp)
                        )
                    }

                    if (state.currentTranslation.isNotBlank()) {
                        Text(
                            text = "↳ ${state.currentTranslation}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    if (state.isPttPressed) {
                        RecordingTimerText(viewModel)
                    } else if (state.lastReceivedMessage != null) {
                        val msg = state.lastReceivedMessage
                        if (msg != null) {
                            val timeString = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            TransmissionIndicator(state = state.transmissionState)

            // Metrics Dashboard — shows full pipeline performance for PS evaluation
            MetricsDashboard(
                sendMetrics = state.lastSendMetrics,
                receiveMetrics = state.lastReceiveMetrics,
                referenceTranscript = state.referenceTranscript,
                onReferenceTranscriptChanged = viewModel::setReferenceTranscript
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Control Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(vertical = 12.dp)
            ) {
                // Edit/Clear button
                IconButton(onClick = { viewModel.clearCurrent() }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Auto Mode Toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = state.isAutoMode,
                        onCheckedChange = { 
                            if (audioPermissionState.status.isGranted) {
                                viewModel.toggleAutoMode()
                            } else {
                                audioPermissionState.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                    Text("Auto Mode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }

                // Main PTT Button
                PTTButton(
                    isPressed = state.isPttPressed,
                    isEnabled = true, // Always enabled so you can test STT locally
                    isProcessing = state.isProcessing,
                    onPressStart = {
                        if (audioPermissionState.status.isGranted) {
                            if (state.isAlertMode) {
                                viewModel.deactivateAlertMode()
                            }
                            viewModel.onPttPressed()
                        } else {
                            audioPermissionState.launchPermissionRequest()
                        }
                    },
                    onPressEnd = {
                        if (audioPermissionState.status.isGranted) {
                            viewModel.onPttReleased()
                        }
                    }
                )

                // Alert Button
                AlertButton(
                    isEnabled = true, // Always enabled so you can test alerts locally
                    onClick = viewModel::onAlertConfirmed
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun RecordingTimerText(viewModel: HomeViewModel) {
    val durationMs by viewModel.recordingDurationMs.collectAsStateWithLifecycle()
    Text(
        text = formatDuration(durationMs),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

