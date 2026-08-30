package com.geolock.app

import android.app.Application
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.service.DeviceRestrictionManager
import com.geolock.app.service.ProtectionForegroundService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GeoLockApplication : Application() {
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            if (settingsRepository.current().protectionEnabled) {
                DeviceRestrictionManager.apply(this@GeoLockApplication)
                ProtectionForegroundService.start(this@GeoLockApplication)
            }
        }
    }
}
