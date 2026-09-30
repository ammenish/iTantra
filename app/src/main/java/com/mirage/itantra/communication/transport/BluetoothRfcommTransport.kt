package com.mirage.itantra.communication.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.mirage.itantra.domain.model.ConnectionState
import com.mirage.itantra.domain.model.Peer
import com.mirage.itantra.domain.model.TransportType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Bluetooth RFCOMM transport implementation for iTantra.
 *
 * Provides a secondary transport channel that demonstrates the
 * transport-independent architecture. The same iTantra packets
 * that flow over Wi-Fi Direct can flow over Bluetooth Classic
 * RFCOMM without any changes to the speech/packet pipeline.
 *
 * Architecture:
 * ```
 *   iTantra Packet Layer
 *         ↓
 *   Transport Interface
 *         ↓
 *   ┌─────────────────────┐
 *   │ WifiDirectTransport │  ← Primary (existing)
 *   │ BluetoothTransport  │  ← Secondary (this)
 *   │ SerialTransport     │  ← Future (embedded bridge)
 *   └─────────────────────┘
 * ```
 *
 * Protocol: Simple length-prefixed framing over RFCOMM:
 *   [4 bytes: payload length (big-endian)] [N bytes: payload]
 *
 * This transport is suitable for:
 *  - Phone-to-phone communication without Wi-Fi
 *  - Phone-to-embedded-device bridging (ESP32, Arduino BT module)
 *  - Lower power consumption than Wi-Fi Direct
 *
 * Limitations:
 *  - Bluetooth Classic range: ~10-30m (vs Wi-Fi Direct ~200m)
 *  - RFCOMM throughput: ~3 Mbps max (still far above iTantra packet needs)
 *  - Requires pairing on most Android versions
 *
 * @param context Application context for BluetoothManager access
 */
class BluetoothRfcommTransport(
    private val context: Context
) : Transport {

    companion object {
        private const val TAG = "BtRfcommTransport"

        /** Custom UUID for iTantra Bluetooth service */
        val ITANTRA_SERVICE_UUID: UUID = UUID.fromString("a7e8f9b1-2c3d-4e5f-8a9b-0c1d2e3f4a5b")

        /** Service name for SDP record */
        const val SERVICE_NAME = "iTantra_BT"

        /** Maximum payload size for a single RFCOMM frame */
        const val MAX_FRAME_SIZE = 4096

        /** Length prefix size (4 bytes, big-endian int) */
        const val LENGTH_PREFIX_SIZE = 4
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _discoveredPeers = MutableStateFlow<List<Peer>>(emptyList())
    private val _incomingPackets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var serverJob: Job? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override var connectedPeer: Peer? = null
        private set

    override fun connectionState(): StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override fun discoveredPeers(): Flow<List<Peer>> = _discoveredPeers.asStateFlow()
    override fun incomingPackets(): Flow<ByteArray> = _incomingPackets.asSharedFlow()

    /**
     * Start listening for incoming Bluetooth connections.
     * Also starts Bluetooth discovery to find nearby iTantra devices.
     */
    @SuppressLint("MissingPermission")
    override suspend fun startDiscovery(): Result<Unit> = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return@withContext Result.failure(IllegalStateException("Bluetooth not available"))
        }

        try {
            _connectionState.value = ConnectionState.DISCOVERING

            // Start server socket to accept incoming connections
            serverJob = scope.launch {
                acceptConnections()
            }

            // List paired devices as potential peers
            val pairedDevices = bluetoothAdapter.bondedDevices?.map { device ->
                Peer(
                    deviceAddress = device.address,
                    deviceName = device.name ?: "Unknown BT Device",
                    transportType = TransportType.BLUETOOTH_RFCOMM
                )
            } ?: emptyList()

            _discoveredPeers.value = pairedDevices
            Log.d(TAG, "Found ${pairedDevices.size} paired Bluetooth devices")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed", e)
            _connectionState.value = ConnectionState.DISCONNECTED
            Result.failure(e)
        }
    }

    override suspend fun stopDiscovery() {
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
    }

    /**
     * Connect to a discovered peer via Bluetooth RFCOMM.
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(peer: Peer): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.CONNECTING

            val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(peer.deviceAddress as String)
                ?: return@withContext Result.failure(IllegalStateException("Device not found"))

            // Cancel discovery to avoid interference
            bluetoothAdapter.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(ITANTRA_SERVICE_UUID)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            connectedPeer = peer
            _connectionState.value = ConnectionState.CONNECTED

            // Start reading incoming data
            startReading()

            Log.d(TAG, "Connected to ${peer.deviceName} (${peer.deviceAddress})")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed to ${peer.deviceName}", e)
            _connectionState.value = ConnectionState.DISCONNECTED
            socket?.close()
            socket = null
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        readJob?.cancel()
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        socket = null
        inputStream = null
        outputStream = null
        connectedPeer = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Send a packet over Bluetooth RFCOMM with length-prefix framing.
     *
     * Frame format: [4-byte length (big-endian)] [payload bytes]
     */
    override suspend fun send(packet: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val os = outputStream
        if (os == null || socket?.isConnected != true) {
            return@withContext Result.failure(IOException("Not connected"))
        }

        try {
            // Write length prefix (4 bytes, big-endian)
            val lengthPrefix = ByteBuffer.allocate(LENGTH_PREFIX_SIZE)
                .putInt(packet.size)
                .array()

            os.write(lengthPrefix)
            os.write(packet)
            os.flush()

            Log.d(TAG, "Sent ${packet.size} bytes over Bluetooth RFCOMM")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * Accept incoming Bluetooth connections (server mode).
     */
    @SuppressLint("MissingPermission")
    private suspend fun acceptConnections() = withContext(Dispatchers.IO) {
        try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                SERVICE_NAME, ITANTRA_SERVICE_UUID
            )

            Log.d(TAG, "Listening for incoming Bluetooth connections...")

            while (isActive) {
                val clientSocket = serverSocket?.accept() ?: break

                // Handle the connection
                socket = clientSocket
                inputStream = clientSocket.inputStream
                outputStream = clientSocket.outputStream

                val device = clientSocket.remoteDevice
                connectedPeer = Peer(
                    deviceAddress = device?.address ?: "unknown",
                    deviceName = device?.name ?: "BT Peer",
                    transportType = TransportType.BLUETOOTH_RFCOMM
                )
                _connectionState.value = ConnectionState.CONNECTED

                Log.d(TAG, "Accepted connection from ${connectedPeer?.deviceName}")

                startReading()

                // Only handle one connection at a time
                break
            }
        } catch (e: IOException) {
            if (isActive) {
                Log.e(TAG, "Accept failed", e)
            }
        }
    }

    /**
     * Continuously read length-prefixed frames from the Bluetooth input stream.
     */
    private fun startReading() {
        readJob = scope.launch {
            val is_ = inputStream ?: return@launch
            val lengthBuffer = ByteArray(LENGTH_PREFIX_SIZE)

            try {
                while (isActive) {
                    // Read length prefix
                    readFully(is_, lengthBuffer)
                    val payloadLength = ByteBuffer.wrap(lengthBuffer).int

                    if (payloadLength <= 0 || payloadLength > MAX_FRAME_SIZE) {
                        Log.w(TAG, "Invalid frame length: $payloadLength, skipping")
                        continue
                    }

                    // Read payload
                    val payload = ByteArray(payloadLength)
                    readFully(is_, payload)

                    Log.d(TAG, "Received ${payloadLength} bytes over Bluetooth RFCOMM")
                    _incomingPackets.emit(payload)
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Read error, disconnecting", e)
                    disconnect()
                }
            }
        }
    }

    /**
     * Read exactly [buffer.size] bytes from the input stream, blocking until complete.
     */
    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = input.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) throw IOException("Connection closed")
            offset += bytesRead
        }
    }

    override fun release() {
        scope.cancel()
        try {
            socket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error releasing Bluetooth resources", e)
        }
        socket = null
        serverSocket = null
        inputStream = null
        outputStream = null
        connectedPeer = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
