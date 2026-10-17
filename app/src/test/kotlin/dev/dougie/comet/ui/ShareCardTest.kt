package dev.dougie.comet.ui

import dev.dougie.comet.network.NetworkSnapshot
import dev.dougie.comet.network.Transport
import dev.dougie.comet.network.WifiDetails
import dev.dougie.comet.speedtest.Grade
import dev.dougie.comet.speedtest.Phase
import dev.dougie.comet.speedtest.TestState
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardTest {
    private val done = TestState(Phase.Done, pingMs = 12.34, jitterMs = 1.2, downloadMbps = 245.6, uploadMbps = 40.04)
    private val date = LocalDate.of(2026, 10, 17)
    private fun card(network: NetworkSnapshot?, test: TestState = done) = ShareCard.from(test, network, date, Locale.UK)

    @Test fun onlyFinishedTestsCanBeShared() {
        for (phase in Phase.entries - Phase.Done) assertNull("$phase", card(null, done.copy(phase = phase)))
        assertNull(card(null, done.copy(uploadMbps = null)))
        assertNull(card(null, done.copy(pingMs = null)))
        assertNotNull(card(null))
    }

    @Test fun formatsValuesGradesAndJitterForTheMoons() {
        val c = card(null)!!
        assertEquals(listOf("12.3", "246", "40.0"), c.values)
        assertEquals(listOf("jitter 1.2 ms", null, null), c.subs)
        assertEquals(listOf(Grade.Good, Grade.Good, Grade.Good), c.grades)
    }

    @Test fun summaryIsPlainText() {
        assertEquals("Comet speed test: ↓ 246 Mbps · ↑ 40.0 Mbps · 12.3 ms ping", card(null)!!.summary)
    }

    @Test fun subtitleNamesTheConnectionTypeButNeverTheNetwork() {
        val wifi = NetworkSnapshot(
            Transport.Wifi,
            addresses = listOf("192.168.1.20"),
            wifi = WifiDetails("HomeNet", "aa:bb:cc:dd:ee:ff", -50, 5180, "Wi-Fi 6", 866),
        )
        val c = card(wifi)!!
        assertTrue(c.subtitle.startsWith("Wi-Fi 6 · "))
        assertTrue(c.subtitle.contains("2026"))
        listOf("HomeNet", "192.168", "aa:bb").forEach { assertFalse(c.toString().contains(it)) }
    }

    @Test fun subtitleIsJustTheDateWhenOfflineOrUnknown() {
        val noNetwork = card(null)!!.subtitle
        assertFalse(noNetwork.contains("·"))
        assertEquals(noNetwork, card(NetworkSnapshot.Offline)!!.subtitle)
    }

    @Test fun vpnShowsOnTheCard() {
        assertTrue(card(NetworkSnapshot(Transport.Wifi, vpn = true))!!.vpn)
        assertFalse(card(NetworkSnapshot(Transport.Wifi))!!.vpn)
    }
}
