package com.geolock.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.UnlockRepository
import com.geolock.app.data.repository.ZoneRepository
import com.geolock.app.domain.LockManager
import com.geolock.app.domain.ProtectionMonitor
import com.geolock.app.domain.ProtectionSnapshot
import com.geolock.app.domain.ProtectionStatus
import com.geolock.app.domain.Zone
import com.geolock.app.security.UnlockKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val snapshot: ProtectionSnapshot = ProtectionSnapshot(ProtectionStatus.DISABLED),
    val zones: List<Zone> = emptyList(),
    val protectionEnabled: Boolean = false
)

data class GateState(
    val visible: Boolean = false,
    val key: String = "",
    val error: String? = null,
    val pending: GateAction? = null
)

enum class GateAction { SETTINGS, ADD_ZONE, EDIT_ZONE, LOGS }

@HiltViewModel
class HomeViewModel @Inject constructor(
    protectionMonitor: ProtectionMonitor,
    zoneRepository: ZoneRepository,
    private val settingsRepository: SettingsRepository,
    private val unlockRepository: UnlockRepository,
    private val unlockKeyManager: UnlockKeyManager,
    private val lockManager: LockManager
) : ViewModel() {
    val state = combine(
        protectionMonitor.snapshot,
        zoneRepository.zones,
        settingsRepository.settings
    ) { snapshot, zones, settings ->
        HomeUiState(snapshot, zones, settings.protectionEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _gate = MutableStateFlow(GateState())
    val gate = _gate.asStateFlow()

    fun requestProtected(action: GateAction, onAllowed: (GateAction) -> Unit) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            if (!settings.protectionEnabled || unlockRepository.hasSettingsUnlock()) {
                onAllowed(action)
                return@launch
            }
            _gate.update { GateState(visible = true, pending = action) }
        }
    }

    fun updateGateKey(value: String) = _gate.update { it.copy(key = value, error = null) }
    fun dismissGate() = _gate.update { GateState() }

    fun confirmGate(onAllowed: (GateAction) -> Unit) {
        viewModelScope.launch {
            val current = _gate.value
            val action = current.pending ?: return@launch
            if (!unlockKeyManager.verify(current.key)) {
                _gate.update { it.copy(error = "Incorrect key") }
                return@launch
            }
            val minutes = settingsRepository.current().unlockDurationMinutes.let { if (it <= 0) 5 else it }
            unlockRepository.grantSettingsUnlock(minutes)
            lockManager.notifyChanged()
            _gate.value = GateState()
            onAllowed(action)
        }
    }
}
