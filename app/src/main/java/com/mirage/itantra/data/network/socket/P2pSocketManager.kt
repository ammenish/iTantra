package com.mirage.itantra.data.network.socket

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class P2pSocketManager @Inject constructor() {

    companion object {
        private const val TAG = "P2pSocketManager"
        const val PORT = 8988
        const val TIMEOUT = 5000
    }

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    
    private var readJob: Job? = null
    private var writeJob: Job? = null
    
    private val incomingPayloadChannel = Channel<ByteArray>(Channel.BUFFERED)
    val incomingPayloads: Flow<ByteArray> = incomingPayloadChannel.receiveAsFlow()
    
    private val outgoingPayloadChannel = Channel<ByteArray>(Channel.BUFFERED)

    private val _isSocketActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSocketActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _isSocketActive.apply { } // No asStateFlow import needed if we just expose the mutable state as StateFlow interface

    suspend fun startServerSocket() = withContext(Dispatchers.IO) {
        stop()
        try {
            serverSocket = ServerSocket(PORT).apply {
                reuseAddress = true
            }
            Log.d(TAG, "Server socket started on port $PORT, waiting for client...")
            
            val client = serverSocket!!.accept()
            Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}")
            
            setupSocketCommunication(client)
        } catch (e: Exception) {
            Log.e(TAG, "Server socket error", e)
            stop()
        }
    }

    suspend fun startClientSocket(hostAddress: String) = withContext(Dispatchers.IO) {
        stop()
        val maxRetries = 5
        var retryCount = 0
        var connected = false
        
        while (retryCount < maxRetries && !connected && isActive) {
            try {
                Log.d(TAG, "Connecting to GO at $hostAddress:$PORT... (Attempt ${retryCount + 1})")
                val socket = Socket()
                socket.bind(null)
                socket.connect(InetSocketAddress(hostAddress, PORT), TIMEOUT)
                
                Log.d(TAG, "Connected to GO successfully")
                setupSocketCommunication(socket)
                connected = true
            } catch (e: Exception) {
                retryCount++
                Log.e(TAG, "Client socket error on attempt $retryCount: ${e.message}")
                if (retryCount >= maxRetries) {
                    stop()
                } else {
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    private suspend fun setupSocketCommunication(socket: Socket) = coroutineScope {
        activeSocket = socket
        _isSocketActive.value = true
        
        val inputStream = DataInputStream(socket.getInputStream())
        val outputStream = DataOutputStream(socket.getOutputStream())
        
        readJob = launch(Dispatchers.IO) { readLoop(inputStream) }
        writeJob = launch(Dispatchers.IO) { writeLoop(outputStream) }
    }

    private suspend fun readLoop(inputStream: DataInputStream) {
        try {
            while (currentCoroutineContext().isActive) {
                val length = inputStream.readInt()
                if (length > 0 && length < 10 * 1024 * 1024) { // Max 10MB sanity check
                    val buffer = ByteArray(length)
                    inputStream.readFully(buffer)
                    incomingPayloadChannel.send(buffer)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read loop terminated", e)
            stop()
        }
    }

    private suspend fun writeLoop(outputStream: DataOutputStream) {
        try {
            for (payload in outgoingPayloadChannel) {
                outputStream.writeInt(payload.size)
                outputStream.write(payload)
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Write loop terminated", e)
            stop()
        }
    }

    suspend fun sendPayload(payload: ByteArray): Boolean {
        return if (activeSocket?.isConnected == true) {
            Log.d(TAG, "[Phase 1] Sending payload of size: ${payload.size} bytes")
            outgoingPayloadChannel.send(payload)
            true
        } else {
            false
        }
    }

    fun stop() {
        try {
            readJob?.cancel()
            writeJob?.cancel()
            activeSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing sockets", e)
        } finally {
            activeSocket = null
            serverSocket = null
            readJob = null
            writeJob = null
            _isSocketActive.value = false
        }
    }
}
