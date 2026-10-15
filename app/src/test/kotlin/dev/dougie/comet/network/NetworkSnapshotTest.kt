package dev.dougie.comet.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkSnapshotTest {
    private fun wifi(standard: String?) = WifiDetails(null, null, null, null, standard, null)

    @Test fun wifiHeadlineUsesStandardWhenKnown() {
        assertEquals("Wi-Fi 6", NetworkSnapshot(Transport.Wifi, wifi = wifi("Wi-Fi 6")).headline)
    }

    @Test fun wifiHeadlineFallsBackToPlainWifi() {
        assertEquals("Wi-Fi", NetworkSnapshot(Transport.Wifi).headline)
        assertEquals("Wi-Fi", NetworkSnapshot(Transport.Wifi, wifi = wifi(null)).headline)
    }

    @Test fun cellularHeadlineUsesGenerationWhenKnown() {
        val cell = CellularDetails(carrier = "X", networkType = "5G", roaming = false)
        assertEquals("5G", NetworkSnapshot(Transport.Cellular, cellular = cell).headline)
        assertEquals("Cellular", NetworkSnapshot(Transport.Cellular).headline)
    }

    @Test fun otherTransportsUseTheirLabel() {
        assertEquals("Ethernet", NetworkSnapshot(Transport.Ethernet).headline)
        assertEquals("Offline", NetworkSnapshot.Offline.headline)
    }

    @Test fun vpnIsPrefixed() {
        assertEquals("VPN · Wi-Fi 5", NetworkSnapshot(Transport.Wifi, vpn = true, wifi = wifi("Wi-Fi 5")).headline)
        assertEquals("VPN · Cellular", NetworkSnapshot(Transport.Cellular, vpn = true).headline)
    }

    @Test fun vpnWithUnknownUnderlyingTransportIsJustVpn() {
        assertEquals("VPN", NetworkSnapshot(Transport.Other, vpn = true).headline)
    }
}
