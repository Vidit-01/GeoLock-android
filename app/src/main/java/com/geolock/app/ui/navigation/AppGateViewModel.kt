package com.geolock.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.UnlockRepository
import com.geolock.app.domain.ProtectedPackages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppGateState(
    val ready: Boolean = false,
    val onboardingComplete: Boolean = false,
    val needsAppKey: Boolean = false
)

@HiltViewModel
class AppGateViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    unlockRepository: UnlockRepository
) : ViewModel() {
    val gate = combine(settingsRepository.settings, unlockRepository.sessions) { settings, sessions ->
        val now = System.currentTimeMillis()
        val unlocked = sessions.any {
            it.packageName == ProtectedPackages.APP_UNLOCK &&
                (it.expiresAt == null || it.expiresAt > now)
        }
        AppGateState(
            ready = true,
            onboardingComplete = settings.onboardingComplete,
            needsAppKey = settings.onboardingComplete && settings.protectionEnabled && !unlocked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppGateState())
}
