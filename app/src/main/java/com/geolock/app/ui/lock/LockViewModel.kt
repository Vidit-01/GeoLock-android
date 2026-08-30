package com.geolock.app.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.LogRepository
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.UnlockRepository
import com.geolock.app.domain.ActivityEventType
import com.geolock.app.domain.LockManager
import com.geolock.app.domain.UnlockDurations
import com.geolock.app.domain.UnlockMode
import com.geolock.app.security.UnlockKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockUiState(
    val key: String = "",
    val error: String? = null,
    val unlocked: Boolean = false
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val unlockKeyManager: UnlockKeyManager,
    private val unlockRepository: UnlockRepository,
    private val lockManager: LockManager,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state = _state.asStateFlow()

    fun updateKey(value: String) = _state.update { it.copy(key = value, error = null) }

    fun unlock(
        packageName: String,
        appName: String,
        zoneId: String,
        zoneNames: String,
        mode: UnlockMode,
        durationMinutes: Int,
        systemLock: Boolean = false
    ) {
        viewModelScope.launch {
            if (!unlockKeyManager.verify(_state.value.key)) {
                _state.update { it.copy(error = "Incorrect key") }
                return@launch
            }
            val settings = settingsRepository.current()
            val effectiveMinutes = if (durationMinutes == UnlockDurations.UNTIL_LEAVE) {
                UnlockDurations.UNTIL_LEAVE
            } else {
                durationMinutes.takeIf { it > 0 } ?: settings.unlockDurationMinutes
            }
            val minutes = if (effectiveMinutes == UnlockDurations.UNTIL_LEAVE) 5 else effectiveMinutes
            if (systemLock) {
                unlockRepository.grantSettingsUnlock(minutes)
            } else {
                val untilLeave = mode == UnlockMode.UNTIL_LEAVE_ZONE ||
                    effectiveMinutes == UnlockDurations.UNTIL_LEAVE ||
                    settings.unlockDurationMinutes == UnlockDurations.UNTIL_LEAVE
                if (untilLeave && zoneId.isNotBlank()) {
                    unlockRepository.grantZoneUnlock(zoneId)
                } else {
                    unlockRepository.grantTemporary(packageName, minutes)
                }
            }
            logRepository.log(
                type = ActivityEventType.APP_UNLOCKED,
                message = "$appName unlocked",
                packageName = packageName,
                appName = appName,
                zoneId = zoneId.ifBlank { null },
                zoneName = zoneNames.ifBlank { null }
            )
            lockManager.notifyChanged()
            _state.update { it.copy(unlocked = true) }
        }
    }
}
