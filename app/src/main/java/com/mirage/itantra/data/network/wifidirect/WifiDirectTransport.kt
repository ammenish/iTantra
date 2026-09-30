package com.mirage.itantra.data.network.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.mirage.itantra.data.network.socket.P2pSocketManager
import com.mirage.itantra.data.packet.ITantraPacket
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.Peer
import com.mirage.itantra.domain.network.P2pTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiDirectTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val socketManager: P2pSocketManager
) : P2pTransport {

    companion object {
        private const val TAG = "WifiDirectTransport"
    }

    private val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    override val incomingPayloads: Flow<ByteArray> = socketManager.incomingPayloads

    private val scope = CoroutineScope(Dispatchers.IO)
    private var socketJob: Job? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    init {
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
        registerReceiver()
        
        scope.launch {
            socketManager.isSocketActive.collect { isActive ->
                // If the socket drops while we think we are connected, force tear down the group
                if (!isActive && _connectionState.value == ConnectionState.CONNECTED) {
                    Log.w(TAG, "Socket connection lost! Forcing disconnect...")
                    disconnect()
                }
            }
        }
    }

    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager?.requestPeers(channel) { peerList ->
                            val mappedPeers = peerList.deviceList.map { device ->
                                Peer(
                                    deviceAddress = device.deviceAddress,
                                    deviceName = device.deviceName ?: "Unknown"
                                )
                            }
                            _peers.value = mappedPeers
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            manager?.requestConnectionInfo(channel) { info ->
                                _connectionState.value = ConnectionState.CONNECTED
                                
                                // Prevent duplicate broadcasts from breaking the socket setup
                                if (socketJob?.isActive == true) return@requestConnectionInfo
                                
                                socketJob = scope.launch {
                                    // Launch socket connection in a child coroutine
                                    launch {
                                        if (info.groupFormed && info.isGroupOwner) {
                                            socketManager.startServerSocket()
                                        } else if (info.groupFormed) {
                                            val host = info.groupOwnerAddress.hostAddress
                                            if (host != null) {
                                                socketManager.startClientSocket(host)
                                            }
                                        }
                                    }
                                    
                                    // Concurrent timeout check: if after 10 seconds the TCP socket still isn't active, tear down
                                    launch {
                                        kotlinx.coroutines.delay(10000)
                                        if (!socketManager.isSocketActive.value && _connectionState.value == ConnectionState.CONNECTED) {
                                            Log.w(TAG, "Socket failed to establish after 10 seconds, forcing disconnect.")
                                            disconnect()
                                        }
                                    }
                                }
                            }
                        } else {
                            _connectionState.value = ConnectionState.DISCONNECTED
                            socketManager.stop()
                        }
                    }
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, 
            receiver, 
            intentFilter, 
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    @SuppressLint("MissingPermission")
    override fun discoverPeers() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        
        _connectionState.value = ConnectionState.DISCOVERING
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
            }
            override fun onFailure(reasonCode: Int) {
                Log.e(TAG, "Discovery failed: $reasonCode")
                _connectionState.value = ConnectionState.ERROR
            }
        })
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, null)
        if (_connectionState.value == ConnectionState.DISCOVERING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(peer: Peer) {
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
        }
        _connectionState.value = ConnectionState.CONNECTING
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect initiated")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connect failed: $reason")
                _connectionState.value = ConnectionState.ERROR
            }
        })
    }

    override fun disconnect() {
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionState.value = ConnectionState.DISCONNECTED
                socketManager.stop()
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason")
            }
        })
    }

    override suspend fun sendPayload(payload: ByteArray): Boolean {
        return socketManager.sendPayload(payload)
    }

    override fun release() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister receiver", e)
            }
        }
        receiver = null
        socketManager.stop()
        socketJob?.cancel()
    }
}
