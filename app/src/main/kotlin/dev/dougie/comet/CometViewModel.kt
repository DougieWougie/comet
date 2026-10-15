package dev.dougie.comet

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dougie.comet.network.NetworkMonitor
import dev.dougie.comet.network.NetworkSnapshot
import dev.dougie.comet.network.PublicIpLookup
import dev.dougie.comet.network.PublicIpState
import dev.dougie.comet.speedtest.Phase
import dev.dougie.comet.speedtest.SpeedTestEngine
import dev.dougie.comet.speedtest.TestState
import dev.dougie.comet.speedtest.parseServerUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CometViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = CometSettings(app)
    private val engine = SpeedTestEngine()
    private val monitor = NetworkMonitor(app)
    private val ipLookup = PublicIpLookup(SpeedTestEngine.defaultClient())

    private var testJob: Job? = null

    private val _test = MutableStateFlow(TestState())
    val test: StateFlow<TestState> = _test

    private val _serverUrl = MutableStateFlow(settings.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl

    private val _publicIp = MutableStateFlow<PublicIpState>(PublicIpState.Idle)
    val publicIp: StateFlow<PublicIpState> = _publicIp

    private val _locationGranted = MutableStateFlow(granted(Manifest.permission.ACCESS_FINE_LOCATION))
    val locationGranted: StateFlow<Boolean> = _locationGranted
    private val phoneStateGranted = MutableStateFlow(granted(Manifest.permission.READ_PHONE_STATE))
    val phoneStateGrantedState: StateFlow<Boolean> = phoneStateGranted

    /** Re-subscribes whenever a permission changes so redacted fields are refreshed. */
    val network: StateFlow<NetworkSnapshot?> =
        combine(_locationGranted, phoneStateGranted) { _, _ -> }
            .flatMapLatest { monitor.observe() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // A public-IP result belongs to the network it was fetched on.
        viewModelScope.launch {
            network.map { it?.transport }.distinctUntilChanged().collect { _publicIp.value = PublicIpState.Idle }
        }
    }

    fun toggleTest() {
        if (_test.value.running) stopTest() else startTest()
    }

    private fun startTest() {
        val server = parseServerUrl(_serverUrl.value)
        if (server == null) {
            _test.value = TestState(phase = Phase.Failed, error = "Set a valid server URL in settings")
            return
        }
        testJob = viewModelScope.launch {
            engine.run(server).collect { _test.value = it }
        }
    }

    private fun stopTest() {
        testJob?.cancel()
        _test.value = _test.value.copy(phase = Phase.Idle, liveMbps = 0.0, progress = 0f)
    }

    fun setServerUrl(url: String) {
        settings.serverUrl = url
        _serverUrl.value = url.trim()
    }

    fun lookUpPublicIp() {
        if (_publicIp.value == PublicIpState.Loading) return
        _publicIp.value = PublicIpState.Loading
        viewModelScope.launch {
            _publicIp.value = try {
                PublicIpState.Loaded(ipLookup.lookup())
            } catch (e: Exception) {
                PublicIpState.Failed(e.message ?: "Lookup failed")
            }
        }
    }

    fun refreshPermissions() {
        _locationGranted.value = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        phoneStateGranted.value = granted(Manifest.permission.READ_PHONE_STATE)
    }

    private fun granted(permission: String) =
        getApplication<Application>().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
