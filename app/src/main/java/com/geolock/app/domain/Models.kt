package com.geolock.app.domain

data class Zone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val enabled: Boolean,
    val createdAt: Long,
    val locationLabel: String,
    val unlockMode: UnlockMode,
    val unlockDurationMinutes: Int,
    val blockedApplications: List<BlockedApp> = emptyList(),
    val blockedDomains: List<String> = emptyList()
) {
    val blockedPackageNames: Set<String>
        get() = blockedApplications.map { it.packageName }.toSet()
}

data class BlockedApp(
    val packageName: String,
    val appName: String
)

data class InstalledApp(
    val packageName: String,
    val appName: String
)

enum class UnlockMode {
    TEMPORARY,
    UNTIL_LEAVE_ZONE
}

enum class ProtectionStatus {
    DISABLED,
    ACTIVE,
    DEGRADED
}

data class ProtectionSnapshot(
    val status: ProtectionStatus,
    val reasons: List<String> = emptyList(),
    val activeZones: List<Zone> = emptyList(),
    val lockedApps: List<BlockedApp> = emptyList(),
    val locationUncertain: Boolean = false,
    val lastLocationEvent: String = "",
    val lastForegroundApp: String = "",
    val lastBlockedAttempt: String = ""
)

data class UnlockSession(
    val id: String,
    val packageName: String?,
    val zoneId: String?,
    val mode: UnlockMode,
    val expiresAt: Long?,
    val createdAt: Long
)

enum class ActivityEventType {
    ZONE_ENTER,
    ZONE_EXIT,
    APP_BLOCKED,
    APP_UNLOCKED,
    PROTECTION_DEGRADED,
    PROTECTION_RESTORED,
    SERVICE_STARTED,
    PERMISSION_LOST,
    LOCATION_UNCERTAIN
}

data class ActivityLogEntry(
    val id: Long,
    val timestamp: Long,
    val eventType: ActivityEventType,
    val packageName: String?,
    val appName: String?,
    val zoneId: String?,
    val zoneName: String?,
    val message: String
)

data class DiagnosticStatus(
    val locationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val locationServicesOn: Boolean,
    val geofencingReady: Boolean,
    val accessibilityEnabled: Boolean,
    val protectionEnabled: Boolean,
    val lastLocationEvent: String,
    val lastForegroundApp: String,
    val lastBlockedAttempt: String,
    val locationUncertain: Boolean,
    val activeZoneNames: List<String>
)

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val protectionEnabled: Boolean = false,
    val unlockDurationMinutes: Int = 5,
    val requireKeyEveryTime: Boolean = false,
    val defaultRadiusMeters: Float = 150f,
    val locationUncertain: Boolean = false,
    val geofencesRegistered: Boolean = false,
    val lastLocationEvent: String = "",
    val lastForegroundApp: String = "",
    val lastBlockedAttempt: String = "",
    val activeZoneIds: Set<String> = emptySet()
)

object UnlockDurations {
    val OPTIONS = listOf(5, 15, 30)
    const val UNTIL_LEAVE = -1

    fun label(minutes: Int): String = when (minutes) {
        UNTIL_LEAVE -> "Until leaving zone"
        1 -> "1 minute"
        else -> "$minutes minutes"
    }
}
