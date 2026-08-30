package com.geolock.app.domain

import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.UnlockRepository
import com.geolock.app.data.repository.ZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainFilter @Inject constructor(
    zoneRepository: ZoneRepository,
    settingsRepository: SettingsRepository,
    unlockRepository: UnlockRepository
) {
    val activeDomains: Flow<Set<String>> = combine(
        zoneRepository.zones,
        settingsRepository.settings,
        unlockRepository.sessions
    ) { zones, settings, sessions ->
        if (!settings.protectionEnabled) return@combine emptySet()
        val unlockedZones = sessions
            .filter { it.mode == UnlockMode.UNTIL_LEAVE_ZONE && it.zoneId != null }
            .mapNotNull { it.zoneId }
            .toSet()
        zones
            .filter { it.enabled && it.id in settings.activeZoneIds && it.id !in unlockedZones }
            .flatMap { it.blockedDomains }
            .toSet()
    }
}
