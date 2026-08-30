package com.geolock.app.domain

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.UnlockRepository
import com.geolock.app.data.repository.ZoneRepository
import com.geolock.app.service.DnsVpnService
import com.geolock.app.service.GeoLockAccessibilityService
import com.geolock.app.service.GeoLockDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val unlockRepository: UnlockRepository,
    private val zoneRepository: ZoneRepository
) {
    val snapshot: Flow<ProtectionSnapshot> = combine(
        settingsRepository.settings,
        zoneRepository.zones,
        unlockRepository.sessions
    ) { settings, zones, sessions ->
        buildSnapshot(settings, zones, sessions)
    }

    fun diagnostics(settings: AppSettings, zones: List<Zone>): DiagnosticStatus {
        return DiagnosticStatus(
            locationGranted = hasFineLocation(),
            backgroundLocationGranted = hasBackgroundLocation(),
            locationServicesOn = locationServicesOn(),
            geofencingReady = settings.geofencesRegistered,
            accessibilityEnabled = isAccessibilityEnabled(),
            protectionEnabled = settings.protectionEnabled,
            lastLocationEvent = settings.lastLocationEvent,
            lastForegroundApp = settings.lastForegroundApp,
            lastBlockedAttempt = settings.lastBlockedAttempt,
            locationUncertain = settings.locationUncertain,
            activeZoneNames = zones.filter { it.enabled && it.id in settings.activeZoneIds }.map { it.name }
        )
    }

    private fun buildSnapshot(
        settings: AppSettings,
        zones: List<Zone>,
        sessions: List<UnlockSession>
    ): ProtectionSnapshot {
        if (!settings.protectionEnabled) {
            return ProtectionSnapshot(status = ProtectionStatus.DISABLED)
        }

        val reasons = mutableListOf<String>()
        if (!isAccessibilityEnabled()) reasons += "Accessibility Service is disabled."
        if (!hasFineLocation()) reasons += "Location permission is disabled."
        if (!hasBackgroundLocation()) reasons += "Background location permission is disabled."
        if (!locationServicesOn()) reasons += "Location services are turned off."
        if (!settings.geofencesRegistered) reasons += "Geofences are not registered."
        if (settings.locationUncertain) {
            reasons += "Current location is uncertain. Last known zones are being kept."
        }
        if (!GeoLockDeviceAdminReceiver.isActive(context)) {
            reasons += "Device admin is off, so GeoLock can be uninstalled without extra steps."
        }
        if (DnsVpnService.needsConsent(context)) {
            reasons += "Domain filter is off. Allow the GeoLock VPN to block websites."
        }

        val now = System.currentTimeMillis()
        val unlockedZoneIds = sessions
            .filter { it.mode == UnlockMode.UNTIL_LEAVE_ZONE && it.zoneId != null }
            .mapNotNull { it.zoneId }
            .toSet()
        val temporarilyUnlocked = if (settings.requireKeyEveryTime) {
            emptySet()
        } else {
            sessions
                .filter { it.mode == UnlockMode.TEMPORARY && (it.expiresAt == null || it.expiresAt > now) }
                .mapNotNull { it.packageName }
                .toSet()
        }

        val active = zones.filter { it.enabled && it.id in settings.activeZoneIds }
        val locked = active
            .filter { it.id !in unlockedZoneIds }
            .flatMap { it.blockedApplications }
            .distinctBy { it.packageName }
            .filter { it.packageName !in temporarilyUnlocked }

        val status = if (reasons.isEmpty()) ProtectionStatus.ACTIVE else ProtectionStatus.DEGRADED
        return ProtectionSnapshot(
            status = status,
            reasons = reasons,
            activeZones = active,
            lockedApps = locked,
            locationUncertain = settings.locationUncertain,
            lastLocationEvent = settings.lastLocationEvent,
            lastForegroundApp = settings.lastForegroundApp,
            lastBlockedAttempt = settings.lastBlockedAttempt
        )
    }

    fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = "${context.packageName}/${GeoLockAccessibilityService::class.java.name}"
        if (enabledServices.split(':').any { it.equals(component, ignoreCase = true) }) {
            return true
        }
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    fun hasFineLocation(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasCoarseLocation(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocation(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun locationServicesOn(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isLocationEnabled
    }
}
