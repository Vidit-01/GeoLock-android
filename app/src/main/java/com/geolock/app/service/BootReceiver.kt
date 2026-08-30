package com.geolock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geolock.app.data.repository.LogRepository
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.domain.ActivityEventType
import com.geolock.app.location.GeofenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var geofenceManager: GeofenceManager
    @Inject lateinit var logRepository: LogRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val settings = settingsRepository.current()
                if (settings.protectionEnabled) {
                    logRepository.log(ActivityEventType.SERVICE_STARTED, "Recovering protection after reboot")
                    geofenceManager.refreshGeofences()
                    ProtectionForegroundService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
