package com.geolock.app.domain

import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.UnlockRepository
import com.geolock.app.data.repository.ZoneRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class LockVerdict(
    val locked: Boolean,
    val packageName: String,
    val blockingZones: List<Zone>,
    val systemLock: Boolean = false
)

@Singleton
class LockManager @Inject constructor(
    private val zoneRepository: ZoneRepository,
    private val unlockRepository: UnlockRepository,
    private val settingsRepository: SettingsRepository
) {
    private val _lockStateChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val lockStateChanged: SharedFlow<Unit> = _lockStateChanged

    suspend fun evaluate(packageName: String): LockVerdict {
        val settings = settingsRepository.current()
        if (!settings.protectionEnabled) {
            return LockVerdict(false, packageName, emptyList())
        }

        unlockRepository.clearExpired()
        val sessions = unlockRepository.getSessions()
        val now = System.currentTimeMillis()

        val settingsUnlocked = sessions.any { session ->
            session.packageName == ProtectedPackages.SETTINGS_UNLOCK &&
                (session.expiresAt == null || session.expiresAt > now)
        }
        if (ProtectedPackages.isProtected(packageName)) {
            return LockVerdict(!settingsUnlocked, packageName, emptyList(), systemLock = true)
        }

        if (!settings.requireKeyEveryTime) {
            val temporary = sessions.any { session ->
                session.mode == UnlockMode.TEMPORARY &&
                    session.packageName == packageName &&
                    (session.expiresAt == null || session.expiresAt > now)
            }
            if (temporary) {
                return LockVerdict(false, packageName, emptyList())
            }
        }

        val zones = zoneRepository.getZones()
        val unlockedZoneIds = sessions
            .filter { it.mode == UnlockMode.UNTIL_LEAVE_ZONE && it.zoneId != null }
            .map { it.zoneId!! }
            .toSet()

        val blocking = zones.filter { zone ->
            zone.enabled &&
                zone.id in settings.activeZoneIds &&
                zone.id !in unlockedZoneIds &&
                packageName in zone.blockedPackageNames
        }

        return LockVerdict(blocking.isNotEmpty(), packageName, blocking)
    }

    suspend fun isPackageLocked(packageName: String): Boolean = evaluate(packageName).locked

    suspend fun lockedApps(): List<BlockedApp> {
        val settings = settingsRepository.current()
        if (!settings.protectionEnabled) return emptyList()
        unlockRepository.clearExpired()
        val sessions = unlockRepository.getSessions()
        val now = System.currentTimeMillis()
        val unlockedZoneIds = sessions
            .filter { it.mode == UnlockMode.UNTIL_LEAVE_ZONE && it.zoneId != null }
            .map { it.zoneId!! }
            .toSet()
        val temporarilyUnlocked = if (settings.requireKeyEveryTime) {
            emptySet()
        } else {
            sessions
                .filter { it.mode == UnlockMode.TEMPORARY && (it.expiresAt == null || it.expiresAt > now) }
                .mapNotNull { it.packageName }
                .toSet()
        }

        return zoneRepository.getZones()
            .filter { it.enabled && it.id in settings.activeZoneIds && it.id !in unlockedZoneIds }
            .flatMap { it.blockedApplications }
            .distinctBy { it.packageName }
            .filter { it.packageName !in temporarilyUnlocked }
    }

    suspend fun activeZones(): List<Zone> {
        val settings = settingsRepository.current()
        return zoneRepository.getZones().filter { it.enabled && it.id in settings.activeZoneIds }
    }

    suspend fun enterZone(zoneId: String) {
        val current = settingsRepository.current().activeZoneIds.toMutableSet()
        if (current.add(zoneId)) {
            settingsRepository.setActiveZoneIds(current)
            notifyChanged()
        }
    }

    suspend fun exitZone(zoneId: String) {
        val current = settingsRepository.current().activeZoneIds.toMutableSet()
        current.remove(zoneId)
        settingsRepository.setActiveZoneIds(current)
        unlockRepository.clearZoneUnlocks(zoneId)
        notifyChanged()
    }

    suspend fun replaceActiveZones(zoneIds: Set<String>) {
        val current = settingsRepository.current().activeZoneIds
        if (current != zoneIds) {
            val left = current - zoneIds
            left.forEach { unlockRepository.clearZoneUnlocks(it) }
            settingsRepository.setActiveZoneIds(zoneIds)
            notifyChanged()
        }
    }

    suspend fun notifyChanged() {
        _lockStateChanged.emit(Unit)
    }
}
