package dev.dougie.comet.network

enum class Transport(val label: String) {
    Wifi("Wi-Fi"), Cellular("Cellular"), Ethernet("Ethernet"), Bluetooth("Bluetooth"), Other("Network"), None("Offline"),
}

data class WifiDetails(
    /** Null unless location permission is granted and location services are on. */
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    /** e.g. "Wi-Fi 6". */
    val standard: String?,
    val linkSpeedMbps: Int?,
)

data class CellularDetails(
    val carrier: String?,
    /** e.g. "5G"; null unless READ_PHONE_STATE is granted. */
    val networkType: String?,
    val roaming: Boolean,
)

data class NetworkSnapshot(
    val transport: Transport,
    val vpn: Boolean = false,
    val validated: Boolean = false,
    val metered: Boolean = false,
    val linkDownMbps: Int? = null,
    val linkUpMbps: Int? = null,
    val signalDbm: Int? = null,
    val addresses: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val mtu: Int? = null,
    val interfaceName: String? = null,
    val wifi: WifiDetails? = null,
    val cellular: CellularDetails? = null,
) {
    /** "Wi-Fi 6", "5G", "VPN · Wi-Fi", … for the collapsed bubble. */
    val headline: String
        get() {
            val base = when (transport) {
                Transport.Wifi -> wifi?.standard ?: "Wi-Fi"
                Transport.Cellular -> cellular?.networkType ?: "Cellular"
                else -> transport.label
            }
            return if (vpn) (if (transport == Transport.Other) "VPN" else "VPN · $base") else base
        }

    companion object {
        val Offline = NetworkSnapshot(Transport.None)
    }
}

data class PublicIp(
    val ip: String,
    val isp: String?,
    val asn: String?,
    val city: String?,
    val country: String?,
)

sealed interface PublicIpState {
    data object Idle : PublicIpState
    data object Loading : PublicIpState
    data class Loaded(val info: PublicIp) : PublicIpState
    data class Failed(val message: String) : PublicIpState
}
