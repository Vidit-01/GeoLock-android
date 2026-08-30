package com.geolock.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.domain.AppSettings
import com.geolock.app.domain.ProtectionMonitor
import com.geolock.app.location.GeofenceManager
import com.geolock.app.service.ProtectionForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val geofenceManager: GeofenceManager,
    val protectionMonitor: ProtectionMonitor,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    fun setProtection(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setProtectionEnabled(enabled)
            if (enabled) {
                geofenceManager.refreshGeofences()
                ProtectionForegroundService.start(context)
                com.geolock.app.service.DnsVpnService.start(context)
            } else {
                ProtectionForegroundService.stop(context)
            }
        }
    }

    fun setUnlockDuration(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(unlockDurationMinutes = minutes) }
        }
    }

    fun setRequireKey(require: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(requireKeyEveryTime = require) }
        }
    }

    fun setDefaultRadius(radius: Float) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(defaultRadiusMeters = radius) }
        }
    }

    fun refreshGeofences() {
        viewModelScope.launch { geofenceManager.refreshGeofences() }
    }

    fun evaluateLocation() {
        viewModelScope.launch { geofenceManager.evaluateCurrentLocation() }
    }
}

data class ChangeKeyState(
    val current: String = "",
    val next: String = "",
    val confirm: String = "",
    val message: String? = null,
    val success: Boolean = false
)
