package dev.dougie.comet.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dougie.comet.CometViewModel
import dev.dougie.comet.speedtest.Phase
import dev.dougie.comet.speedtest.TestState
import java.util.Locale

private const val PlanetRadiusFraction = 0.19f
private const val PlanetCenterFraction = 0.46f

@Composable
fun CometApp(vm: CometViewModel, dark: Boolean) {
    val test by vm.test.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val publicIp by vm.publicIp.collectAsStateWithLifecycle()
    val locationGranted by vm.locationGranted.collectAsStateWithLifecycle()
    val phoneGranted by vm.phoneStateGrantedState.collectAsStateWithLifecycle()
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    val view = LocalView.current
    SideEffect { view.keepScreenOn = test.running }

    // Permissions are asked for only when the user taps the matching control in the bubble.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }

    val activeMoon = when (test.phase) {
        Phase.Ping -> 0
        Phase.Download -> 1
        Phase.Upload -> 2
        else -> -1
    }
    val clock = rememberSceneClock(energyForMbps(test.liveMbps), activeMoon)
    val input = SceneInput(
        energy = energyForMbps(test.liveMbps),
        activeMoon = activeMoon,
        moonDone = booleanArrayOf(test.pingMs != null && activeMoon != 0, test.downloadMbps != null && activeMoon != 1, test.uploadMbps != null && activeMoon != 2),
        moonProgress = test.progress,
        dark = dark,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val radiusDp = minOf(maxWidth, maxHeight) * PlanetRadiusFraction
        val centerY = maxHeight * PlanetCenterFraction

        Canvas(Modifier.fillMaxSize()) {
            val r = minOf(size.width, size.height) * PlanetRadiusFraction
            drawSpace(clock, input, r, Offset(size.width / 2, size.height * PlanetCenterFraction))
        }

        Box(
            Modifier
                .offset(x = maxWidth / 2 - radiusDp, y = centerY - radiusDp)
                .size(radiusDp * 2)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = if (test.running) "Stop test" else "Start test") { vm.toggleTest() },
            contentAlignment = Alignment.Center,
        ) {
            PlanetLabel(test)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MoonStat(0, dark, "PING", test.pingMs?.let { format(it) }, "ms", test.jitterMs?.let { "jitter ${format(it)} ms" })
                MoonStat(1, dark, "DOWNLOAD", test.downloadMbps?.let { format(it) }, "Mbps", null)
                MoonStat(2, dark, "UPLOAD", test.uploadMbps?.let { format(it) }, "Mbps", null)
            }
            if (test.phase == Phase.Failed) {
                Text(test.error ?: "Test failed", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            NetworkBubble(
                network = network,
                publicIp = publicIp,
                locationGranted = locationGranted,
                phoneStateGranted = phoneGranted,
                onRequestLocation = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                onRequestPhoneState = { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                onLookUpPublicIp = vm::lookUpPublicIp,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row {
                IconButton(onClick = { vm.setDarkMode(!dark) }) {
                    Text(
                        if (dark) "☀" else "☾", fontSize = 22.sp,
                        modifier = Modifier.semantics { contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode" },
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(serverUrl, onSave = vm::setServerUrl, onDismiss = { showSettings = false })
    }
}

@Composable
private fun PlanetLabel(test: TestState) {
    val (caption, value, unit) = when (test.phase) {
        Phase.Ping -> Triple("PING", test.pingMs?.let { format(it) } ?: "…", "ms")
        Phase.Download -> Triple("DOWNLOAD", format(test.liveMbps), "Mbps")
        Phase.Upload -> Triple("UPLOAD", format(test.liveMbps), "Mbps")
        Phase.Done -> Triple("DONE", "↻", "tap to retest")
        Phase.Failed -> Triple("FAILED", "↻", "tap to retry")
        Phase.Idle -> Triple("READY", "GO", "tap to start")
    }
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 12f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(caption, fontSize = 12.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.text.TextStyle(shadow = shadow))
        Text(value, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White, style = androidx.compose.ui.text.TextStyle(shadow = shadow))
        Text(unit, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), style = androidx.compose.ui.text.TextStyle(shadow = shadow))
    }
}

@Composable
private fun MoonStat(moon: Int, dark: Boolean, label: String, value: String?, unit: String, sub: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, letterSpacing = 1.5.sp, color = moonColors(dark)[moon])
        Text(value ?: "—", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(sub ?: " ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
    }
}

private fun format(v: Double): String = if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
