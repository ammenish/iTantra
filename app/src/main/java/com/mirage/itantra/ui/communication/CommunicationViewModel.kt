package com.mirage.itantra.ui.communication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.Peer
import com.mirage.itantra.domain.model.TransportType
import com.mirage.itantra.domain.network.P2pTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommunicationUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val transportType: TransportType = TransportType.WIFI_DIRECT,
    val discoveredPeers: List<Peer> = emptyList(),
    val connectedPeer: Peer? = null,
    val isDiscovering: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val transport: P2pTransport
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunicationUiState())
    val uiState: StateFlow<CommunicationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            transport.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    connectionState = state,
                    isDiscovering = state == ConnectionState.DISCOVERING
                )
            }
        }

        viewModelScope.launch {
            transport.peers.collect { peers ->
                _uiState.value = _uiState.value.copy(
                    discoveredPeers = peers
                )
            }
        }
    }

    fun startDiscovery() {
        transport.discoverPeers()
    }

    fun stopDiscovery() {
        transport.stopDiscovery()
    }

    fun connectToPeer(peer: Peer) {
        _uiState.value = _uiState.value.copy(
            connectedPeer = peer
        )
        transport.connect(peer)
    }

    fun disconnect() {
        transport.disconnect()
        _uiState.value = _uiState.value.copy(connectedPeer = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
