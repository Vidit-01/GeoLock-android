package com.geolock.app.ui.zone

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.ZoneRepository
import com.geolock.app.domain.AppCatalog
import com.geolock.app.domain.BlockedApp
import com.geolock.app.domain.InstalledApp
import com.geolock.app.domain.DomainNames
import com.geolock.app.domain.UnlockDurations
import com.geolock.app.domain.UnlockMode
import com.geolock.app.domain.Zone
import com.geolock.app.location.DeviceLocation
import com.geolock.app.location.GeofenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZoneEditState(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val locationLabel: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Float = 150f,
    val unlockDurationMinutes: Int = 5,
    val createdAt: Long = 0L,
    val apps: List<InstalledApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val domains: List<String> = emptyList(),
    val domainDraft: String = "",
    val locating: Boolean = false,
    val saving: Boolean = false,
    val isNew: Boolean = true
)

@HiltViewModel
class ZoneEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val zoneRepository: ZoneRepository,
    private val settingsRepository: SettingsRepository,
    private val appCatalog: AppCatalog,
    private val deviceLocation: DeviceLocation,
    private val geofenceManager: GeofenceManager,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val zoneId = savedStateHandle.get<String>("zoneId").orEmpty()
    private val _state = MutableStateFlow(ZoneEditState(isNew = zoneId == "new" || zoneId.isBlank()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val apps = appCatalog.launchableApps().filter { it.packageName != context.packageName }
            val existing = if (zoneId != "new" && zoneId.isNotBlank()) zoneRepository.getZone(zoneId) else null
            _state.update {
                if (existing == null) {
                    it.copy(apps = apps, radius = settings.defaultRadiusMeters, unlockDurationMinutes = settings.unlockDurationMinutes)
                } else {
                    it.copy(
                        id = existing.id,
                        name = existing.name,
                        enabled = existing.enabled,
                        locationLabel = existing.locationLabel,
                        latitude = existing.latitude,
                        longitude = existing.longitude,
                        radius = existing.radius,
                        createdAt = existing.createdAt,
                        unlockDurationMinutes = if (existing.unlockMode == UnlockMode.UNTIL_LEAVE_ZONE) {
                            UnlockDurations.UNTIL_LEAVE
                        } else {
                            existing.unlockDurationMinutes
                        },
                        apps = apps,
                        selectedPackages = existing.blockedPackageNames,
                        domains = existing.blockedDomains,
                        isNew = false
                    )
                }
            }
        }
    }

    fun updateName(value: String) = _state.update { it.copy(name = value) }
    fun updateEnabled(value: Boolean) = _state.update { it.copy(enabled = value) }
    fun updateRadius(value: Float) = _state.update { it.copy(radius = value) }
    fun updateUnlock(value: Int) = _state.update { it.copy(unlockDurationMinutes = value) }
    fun updateDomainDraft(value: String) = _state.update { it.copy(domainDraft = value) }

    fun addDomain() = _state.update { current ->
        val domain = DomainNames.normalize(current.domainDraft)
        if (domain.isBlank() || domain in current.domains) current
        else current.copy(domains = current.domains + domain, domainDraft = "")
    }

    fun removeDomain(domain: String) = _state.update { it.copy(domains = it.domains - domain) }

    fun toggleApp(packageName: String) = _state.update {
        val next = it.selectedPackages.toMutableSet()
        if (!next.add(packageName)) next.remove(packageName)
        it.copy(selectedPackages = next)
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            val location = deviceLocation.current()
            if (location != null) {
                val label = deviceLocation.reverse(location.latitude, location.longitude).ifBlank { "Current location" }
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
            val place = deviceLocation.search(query).firstOrNull() ?: return@launch
            _state.update {
                it.copy(latitude = place.latitude, longitude = place.longitude, locationLabel = place.label)
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val current = _state.value
        val lat = current.latitude ?: return
        val lng = current.longitude ?: return
        if (current.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val selected = current.apps.filter { it.packageName in current.selectedPackages }
            val untilLeave = current.unlockDurationMinutes == UnlockDurations.UNTIL_LEAVE
            zoneRepository.saveZone(
                Zone(
                    id = current.id,
                    name = current.name.trim(),
                    latitude = lat,
                    longitude = lng,
                    radius = current.radius,
                    enabled = current.enabled,
                    createdAt = current.createdAt,
                    locationLabel = current.locationLabel.ifBlank { current.name },
                    unlockMode = if (untilLeave) UnlockMode.UNTIL_LEAVE_ZONE else UnlockMode.TEMPORARY,
                    unlockDurationMinutes = if (untilLeave) 5 else current.unlockDurationMinutes,
                    blockedApplications = selected.map { BlockedApp(it.packageName, it.appName) },
                    blockedDomains = current.domains
                )
            )
            geofenceManager.refreshGeofences()
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.id
        if (id.isBlank()) {
            onDone()
            return
        }
        viewModelScope.launch {
            zoneRepository.deleteZone(id)
            geofenceManager.refreshGeofences()
            onDone()
        }
    }
}
