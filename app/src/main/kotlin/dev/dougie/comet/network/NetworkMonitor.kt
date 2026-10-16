package dev.dougie.comet.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Observes the default network. Collect it again after a permission is granted: Wi-Fi location
 * redaction and telephony access are decided when the callback delivers, not when it is registered.
 */
class NetworkMonitor(private val context: Context) {

    fun observe(): Flow<NetworkSnapshot> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        var caps: NetworkCapabilities? = null
        var props: LinkProperties? = null

        fun push() {
            val c = caps ?: return
            trySend(snapshot(c, props, vpnElsewhere = vpnOnDevice(cm)))
        }

        // FLAG_INCLUDE_LOCATION_INFO is required for WifiInfo to carry SSID/BSSID.
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onAvailable(network: Network) {
                caps = null
                props = null
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                caps = capabilities
                push()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                props = linkProperties
                push()
            }

            override fun onLost(network: Network) {
                caps = null
                props = null
                trySend(NetworkSnapshot.Offline)
            }
        }

        // Callbacks arrive on one thread, so caps/props need no locking.
        val handler = Handler(Looper.getMainLooper())
        cm.registerDefaultNetworkCallback(callback, handler)

        // A VPN that bypasses this app sends no callbacks, so look for one periodically.
        val poll = object : Runnable {
            override fun run() {
                push()
                handler.postDelayed(this, VpnPollMillis)
            }
        }
        handler.postDelayed(poll, VpnPollMillis)
        awaitClose {
            handler.removeCallbacks(poll)
            cm.unregisterNetworkCallback(callback)
        }
    }.conflate()

    /** True if any VPN network exists, including one this app's traffic does not use (split tunnelling). */
    @Suppress("DEPRECATION")
    private fun vpnOnDevice(cm: ConnectivityManager) = cm.allNetworks.any {
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun snapshot(caps: NetworkCapabilities, props: LinkProperties?, vpnElsewhere: Boolean): NetworkSnapshot {
        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.Ethernet
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> Transport.Bluetooth
            else -> Transport.Other
        }
        return NetworkSnapshot(
            transport = transport,
            vpn = vpn,
            vpnElsewhere = vpnElsewhere && !vpn,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            linkDownMbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 }?.div(1000),
            linkUpMbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 }?.div(1000),
            signalDbm = caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED },
            addresses = props?.linkAddresses?.mapNotNull { it.address.hostAddress } ?: emptyList(),
            dnsServers = props?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
            mtu = props?.mtu?.takeIf { it > 0 },
            interfaceName = props?.interfaceName,
            wifi = (caps.transportInfo as? WifiInfo)?.let(::wifiDetails),
            cellular = if (transport == Transport.Cellular) cellularDetails() else null,
        )
    }

    private fun wifiDetails(info: WifiInfo): WifiDetails {
        val ssid = info.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManagerUnknownSsid || it.isBlank() }
        val bssid = info.bssid?.takeUnless { it == RedactedBssid }
        return WifiDetails(
            ssid = ssid,
            bssid = bssid,
            rssiDbm = info.rssi.takeIf { it > -127 },
            frequencyMhz = info.frequency.takeIf { it > 0 },
            standard = when (info.wifiStandard) {
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
                else -> null
            },
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
        )
    }

    private fun cellularDetails(): CellularDetails {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val canReadType = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val type = if (canReadType) {
            try {
                networkGeneration(tm.dataNetworkType)
            } catch (_: SecurityException) {
                null
            }
        } else null
        return CellularDetails(
            carrier = tm.networkOperatorName?.takeIf { it.isNotBlank() },
            networkType = type,
            roaming = tm.isNetworkRoaming,
        )
    }

    private fun networkGeneration(type: Int): String? = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        else -> null
    }

    private companion object {
        const val WifiManagerUnknownSsid = "<unknown ssid>"
        const val VpnPollMillis = 3_000L
        const val RedactedBssid = "02:00:00:00:00:00"
    }
}
