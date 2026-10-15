package dev.dougie.comet.ui

import android.content.Context
import android.location.LocationManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dougie.comet.network.NetworkSnapshot
import dev.dougie.comet.network.PublicIpLookup
import dev.dougie.comet.network.PublicIpState
import dev.dougie.comet.network.Transport

@Composable
fun NetworkBubble(
    network: NetworkSnapshot?,
    publicIp: PublicIpState,
    locationGranted: Boolean,
    phoneStateGranted: Boolean,
    onRequestLocation: () -> Unit,
    onRequestPhoneState: () -> Unit,
    onLookUpPublicIp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val online = network != null && network.transport != Transport.None
    val subtitle = when {
        network == null -> "Detecting…"
        network.wifi?.ssid != null -> network.wifi.ssid
        network.cellular?.carrier != null -> network.cellular.carrier
        else -> null
    }

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .animateContentSize()
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        shape = RoundedCornerShape(if (expanded) 24.dp else 28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    ) {
        Column(Modifier.clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse network info" else "Expand network info") { expanded = !expanded }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(if (online && network.validated) Color(0xFF5CE08A) else if (online) Color(0xFFFFB454) else Color(0xFFFF6B6B)),
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Text(network?.headline ?: "Network", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
                Text(if (expanded) "  ▴" else "  ▾", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (expanded && network != null) {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Details(network, publicIp, locationGranted, phoneStateGranted, onRequestLocation, onRequestPhoneState, onLookUpPublicIp)
                }
            }
        }
    }
}

@Composable
private fun Details(
    n: NetworkSnapshot,
    publicIp: PublicIpState,
    locationGranted: Boolean,
    phoneStateGranted: Boolean,
    onRequestLocation: () -> Unit,
    onRequestPhoneState: () -> Unit,
    onLookUpPublicIp: () -> Unit,
) {
    if (n.transport == Transport.None) {
        Text("No connection", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        return
    }

    val wifi = n.wifi
    if (wifi != null) {
        if (wifi.ssid != null) {
            InfoRow("Network", wifi.ssid)
        } else if (!locationGranted) {
            InfoRow("Network") { TextButton(onClick = onRequestLocation) { Text("Show Wi-Fi name") } }
        } else {
            val locationOn = LocalContext.current.getSystemService(LocationManager::class.java).isLocationEnabled
            InfoRow("Network", if (locationOn) "Hidden" else "Turn on Location to see the name")
        }
        wifi.bssid?.let { InfoRow("BSSID", it) }
        wifi.rssiDbm?.let { InfoRow("Signal", "$it dBm") }
        wifi.frequencyMhz?.let { InfoRow("Frequency", "$it MHz (${band(it)})") }
        wifi.standard?.let { InfoRow("Standard", it) }
        wifi.linkSpeedMbps?.let { InfoRow("Link speed", "$it Mbps") }
    }

    n.cellular?.let { cell ->
        cell.carrier?.let { InfoRow("Carrier", it) }
        if (cell.networkType != null) {
            InfoRow("Network type", cell.networkType)
        } else if (!phoneStateGranted) {
            InfoRow("Network type") { TextButton(onClick = onRequestPhoneState) { Text("Show 4G/5G") } }
        }
        InfoRow("Roaming", if (cell.roaming) "Yes" else "No")
        n.signalDbm?.let { InfoRow("Signal", "$it dBm") }
    }

    if (wifi == null) n.signalDbm?.takeIf { n.cellular == null }?.let { InfoRow("Signal", "$it dBm") }
    if (n.linkDownMbps != null || n.linkUpMbps != null) {
        InfoRow("Link estimate", "↓ ${n.linkDownMbps ?: "?"} / ↑ ${n.linkUpMbps ?: "?"} Mbps")
    }
    if (n.addresses.isNotEmpty()) InfoRow("IP", n.addresses.joinToString("\n"))
    if (n.dnsServers.isNotEmpty()) InfoRow("DNS", n.dnsServers.joinToString("\n"))
    n.mtu?.let { InfoRow("MTU", it.toString()) }
    n.interfaceName?.let { InfoRow("Interface", it) }
    InfoRow("Internet", if (n.validated) "Verified" else "Not verified")
    InfoRow("Metered", if (n.metered) "Yes" else "No")
    if (n.vpn) InfoRow("VPN", "Active")

    PublicIpSection(publicIp, onLookUpPublicIp)
}

@Composable
private fun PublicIpSection(state: PublicIpState, onLookUp: () -> Unit) {
    Text("Public IP", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    when (state) {
        PublicIpState.Idle, is PublicIpState.Failed -> {
            Text(
                "Looking this up sends your IP address to ${PublicIpLookup.HOST}, a third party.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            if (state is PublicIpState.Failed) Text(state.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onLookUp) { Text("Look up public IP") }
        }
        PublicIpState.Loading -> Text("Asking ${PublicIpLookup.HOST}…", fontSize = 13.sp)
        is PublicIpState.Loaded -> {
            val i = state.info
            InfoRow("Address", i.ip)
            i.isp?.let { InfoRow("ISP", it) }
            i.asn?.let { InfoRow("ASN", it) }
            val place = listOfNotNull(i.city, i.country).joinToString(", ")
            if (place.isNotEmpty()) InfoRow("Location", place)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) = InfoRow(label) { Text(value, fontSize = 13.sp) }

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.width(112.dp))
        value()
    }
}

private fun band(mhz: Int) = when {
    mhz < 3000 -> "2.4 GHz"
    mhz < 5925 -> "5 GHz"
    else -> "6 GHz"
}
