package com.mirage.itantra.ui.communication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.Peer
import com.mirage.itantra.domain.model.TransportType
import com.mirage.itantra.ui.components.AnimatedBackground
import com.mirage.itantra.ui.components.WaveformRenderer
import com.mirage.itantra.ui.theme.*

import android.Manifest
import android.os.Build
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CommunicationScreen(
    onNavigateBack: () -> Unit = {},
    onAlertTriggered: () -> Unit = {},
    viewModel: CommunicationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    val permissionsToRequest = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    AnimatedBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Connection",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            // Header Waveform (subtle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                WaveformRenderer(
                    amplitude = 0.1f, 
                    isCircular = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport selector
            Text(
                text = "TRANSPORT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TransportChip(
                    label = "Wi-Fi Direct",
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = it) },
                    isSelected = state.transportType == TransportType.WIFI_DIRECT,
                    modifier = Modifier.weight(1f)
                )
                TransportChip(
                    label = "Bluetooth",
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = it) },
                    isSelected = state.transportType == TransportType.BLUETOOTH_RFCOMM,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Discovery / Connection Controls
            val isAlertMode = LocalAlertMode.current
            val btnColor = if (isAlertMode) PrimaryRed else PrimaryGreen

            when (state.connectionState) {
                ConnectionState.CONNECTED -> {
                    OutlinedButton(
                        onClick = viewModel::disconnect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DisconnectedRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Disconnect from ${state.connectedPeer?.callsign ?: "peer"}")
                    }
                }
                ConnectionState.DISCOVERING -> {
                    Button(
                        onClick = viewModel::stopDiscovery,
                        colors = ButtonDefaults.buttonColors(containerColor = ConnectingAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Stop Discovery")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            if (permissionState.allPermissionsGranted) {
                                viewModel.startDiscovery()
                            } else {
                                permissionState.launchMultiplePermissionRequest()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = btnColor, contentColor = OnPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Discover Devices")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Discovered Peers
            Text(
                text = "AVAILABLE DEVICES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (state.discoveredPeers.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(32.dp)
                ) {
                    Text(
                        text = if (state.isDiscovering) "Searching for nearby iTantra devices…"
                        else "Tap \"Discover Devices\" to search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.discoveredPeers) { peer ->
                        PeerItem(
                            peer = peer,
                            onClick = { viewModel.connectToPeer(peer) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerItem(peer: Peer, onClick: () -> Unit) {
    val isAlertMode = LocalAlertMode.current
    val accent = if (isAlertMode) PrimaryRed else PrimaryGreen

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = peer.callsign,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = peer.deviceAddress,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "CONNECT",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TransportChip(
    label: String,
    icon: @Composable (Modifier) -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val isAlertMode = LocalAlertMode.current
    val accent = if (isAlertMode) PrimaryRed else PrimaryGreen

    val bg = if (isSelected) accent.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    
    val textColor = if (isSelected) accent
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(vertical = 12.dp)
    ) {
        icon(Modifier.padding(end = 6.dp).size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
