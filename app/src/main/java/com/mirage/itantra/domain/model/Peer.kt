package com.mirage.itantra.domain.model

/**
 * Represents a discovered or connected peer device.
 *
 * @property deviceAddress MAC address or unique device identifier
 * @property deviceName Human-readable device name
 * @property callsign User-configured iTantra callsign (e.g., "MIRAGE-BRAVO-02")
 * @property isGroupOwner Whether this peer is the Wi-Fi Direct group owner
 * @property transportType The transport through which this peer was discovered
 */
data class Peer(
    val deviceAddress: String,
    val deviceName: String,
    val callsign: String = deviceName,
    val isGroupOwner: Boolean = false,
    val transportType: TransportType = TransportType.WIFI_DIRECT
)

/**
 * Transport types supported by iTantra.
 */
enum class TransportType {
    WIFI_DIRECT,
    BLUETOOTH_RFCOMM
}
