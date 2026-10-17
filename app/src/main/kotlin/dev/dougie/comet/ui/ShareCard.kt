package dev.dougie.comet.ui

import dev.dougie.comet.network.NetworkSnapshot
import dev.dougie.comet.network.Transport
import dev.dougie.comet.speedtest.Grading
import dev.dougie.comet.speedtest.Phase
import dev.dougie.comet.speedtest.TestState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * What goes on the shareable result image. Deliberately holds no Wi-Fi name, address or other
 * identifying network detail: only the connection type, so a card is safe to post anywhere.
 */
data class ShareCard(
    val pingMs: Double,
    val jitterMs: Double?,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val vpn: Boolean,
    /** Connection type and date, e.g. "Wi-Fi 6 · 17 Oct 2026". */
    val subtitle: String,
) {
    /** Moon order: ping, download, upload. */
    val values get() = listOf(format(pingMs), format(downloadMbps), format(uploadMbps))
    val subs get() = listOf(jitterMs?.let { "jitter ${format(it)} ms" }, null, null)
    val grades get() = listOf(Grading.ping(pingMs), Grading.download(downloadMbps), Grading.upload(uploadMbps))

    /** Plain-text version sent alongside the image. */
    val summary get() = "Comet speed test: ↓ ${format(downloadMbps)} Mbps · ↑ ${format(uploadMbps)} Mbps · ${format(pingMs)} ms ping"

    companion object {
        /** Null unless [test] finished with all three measurements. */
        fun from(test: TestState, network: NetworkSnapshot?, date: LocalDate, locale: Locale = Locale.getDefault()): ShareCard? {
            if (test.phase != Phase.Done) return null
            val ping = test.pingMs ?: return null
            val down = test.downloadMbps ?: return null
            val up = test.uploadMbps ?: return null
            val day = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            val online = network?.takeIf { it.transport != Transport.None }
            return ShareCard(ping, test.jitterMs, down, up, online?.vpn == true, listOfNotNull(online?.headline, day).joinToString(" · "))
        }
    }
}
