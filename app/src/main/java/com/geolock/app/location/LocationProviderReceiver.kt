package com.geolock.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geolock.app.data.repository.LogRepository
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.domain.ActivityEventType
import com.geolock.app.domain.ProtectionMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationProviderReceiver : BroadcastReceiver() {
    @Inject lateinit var protectionMonitor: ProtectionMonitor
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var logRepository: LogRepository
    @Inject lateinit var geofenceManager: GeofenceManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                if (protectionMonitor.locationServicesOn()) {
                    settingsRepository.setLocationUncertain(false)
                    geofenceManager.evaluateCurrentLocation()
                } else {
                    settingsRepository.setLocationUncertain(true)
                    logRepository.log(
                        ActivityEventType.LOCATION_UNCERTAIN,
                        "Location services turned off. Keeping last known zone state."
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}