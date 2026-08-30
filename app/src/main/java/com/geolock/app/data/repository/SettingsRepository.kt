package com.geolock.app.data.repository

import com.geolock.app.data.local.SettingsDataStore
import com.geolock.app.domain.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore
) {
    val settings: Flow<AppSettings> = dataStore.settings

    suspend fun current(): AppSettings = dataStore.settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) = dataStore.update(transform)

    suspend fun setProtectionEnabled(enabled: Boolean) {
        dataStore.update { it.copy(protectionEnabled = enabled) }
    }

    suspend fun setOnboardingComplete() {
        dataStore.update { it.copy(onboardingComplete = true) }
    }

    suspend fun setActiveZoneIds(ids: Set<String>) {
        dataStore.update { it.copy(activeZoneIds = ids) }
    }

    suspend fun setLocationUncertain(uncertain: Boolean) {
        dataStore.update { it.copy(locationUncertain = uncertain) }
    }

    suspend fun setGeofencesRegistered(registered: Boolean) {
        dataStore.update { it.copy(geofencesRegistered = registered) }
    }

    suspend fun setLastLocationEvent(event: String) {
        dataStore.update { it.copy(lastLocationEvent = event) }
    }

    suspend fun setLastForegroundApp(app: String) {
        dataStore.update { it.copy(lastForegroundApp = app) }
    }

    suspend fun setLastBlockedAttempt(text: String) {
        dataStore.update { it.copy(lastBlockedAttempt = text) }
    }
}
