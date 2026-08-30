package com.geolock.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.ZoneRepository
import com.geolock.app.domain.AppCatalog
import com.geolock.app.domain.BlockedApp
import com.geolock.app.domain.InstalledApp
import com.geolock.app.domain.ProtectionMonitor
import com.geolock.app.domain.UnlockMode
import com.geolock.app.domain.Zone
import com.geolock.app.location.DeviceLocation
import com.geolock.app.location.GeofenceManager
import com.geolock.app.security.UnlockKeyManager
import com.geolock.app.service.ProtectionForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val step: Int = 0,
    val key: String = "",
    val confirmKey: String = "",
    val keyError: String? = null,
    val zoneName: String = "College",
    val locationLabel: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Float = 150f,
    val apps: List<InstalledApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val locating: Boolean = false,
    val saving: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val zoneRepository: ZoneRepository,
    private val unlockKeyManager: UnlockKeyManager,
    private val appCatalog: AppCatalog,
    private val deviceLocation: DeviceLocation,
    private val geofenceManager: GeofenceManager,
    val protectionMonitor: ProtectionMonitor,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state = _state.asStateFlow()

    val boot = settingsRepository.settings
        .map { BootState(ready = true, onboardingComplete = it.onboardingComplete) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BootState())

    data class BootState(
        val ready: Boolean = false,
        val onboardingComplete: Boolean = false
    )

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            _state.update {
                it.copy(
                    radius = settings.defaultRadiusMeters,
                    apps = appCatalog.launchableApps().filter { app -> app.packageName != context.packageName }
                )
            }
        }
    }

    fun next() = _state.update { it.copy(step = it.step + 1, keyError = null) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), keyError = null) }

    fun updateKey(value: String) = _state.update { it.copy(key = value, keyError = null) }
    fun updateConfirm(value: String) = _state.update { it.copy(confirmKey = value, keyError = null) }
    fun updateZoneName(value: String) = _state.update { it.copy(zoneName = value) }
    fun updateRadius(value: Float) = _state.update { it.copy(radius = value) }
    fun toggleApp(packageName: String) = _state.update {
        val next = it.selectedPackages.toMutableSet()
        if (!next.add(packageName)) next.remove(packageName)
        it.copy(selectedPackages = next)
    }

    fun saveKey(): Boolean {
        val current = _state.value
        if (current.key.length < UnlockKeyManager.MIN_LENGTH) {
            _state.update { it.copy(keyError = "Use at least ${UnlockKeyManager.MIN_LENGTH} characters.") }
            return false
        }
        if (current.key != current.confirmKey) {
            _state.update { it.copy(keyError = "Keys do not match.") }
            return false
        }
        viewModelScope.launch { unlockKeyManager.setKey(current.key) }
        next()
        return true
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            val location = deviceLocation.current()
            if (location != null) {
                val label = deviceLocation.reverse(location.latitude, location.longitude)
                    .ifBlank { "Current location" }
                _state.update {
                    it.copy(
                        locating = false,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        locationLabel = label
                    )
                }
            } else {
                _state.update { it.copy(locating = false) }
            }
        }
    }

    fun searchLocation(query: String) {
        viewModelScope.launch {
            val places = deviceLocation.search(query)
            val place = places.firstOrNull() ?: return@launch
            _state.update {
                it.copy(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    locationLabel = place.label
                )
            }
        }
    }

    fun finish(onDone: () -> Unit) {
        val current = _state.value
        val lat = current.latitude
        val lng = current.longitude
        if (lat == null || lng == null || current.zoneName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val selected = current.apps.filter { it.packageName in current.selectedPackages }
            zoneRepository.saveZone(
                Zone(
                    id = "",
                    name = current.zoneName.trim(),
                    latitude = lat,
                    longitude = lng,
                    radius = current.radius,
                    enabled = true,
                    createdAt = 0L,
                    locationLabel = current.locationLabel.ifBlank { current.zoneName },
                    unlockMode = UnlockMode.TEMPORARY,
                    unlockDurationMinutes = 5,
                    blockedApplications = selected.map { BlockedApp(it.packageName, it.appName) }
                )
            )
            settingsRepository.update {
                it.copy(onboardingComplete = true, protectionEnabled = true)
            }
            geofenceManager.refreshGeofences()
            ProtectionForegroundService.start(context)
            com.geolock.app.service.DnsVpnService.start(context)
            _state.update { it.copy(saving = false) }
            onDone()
        }
    }
}
