package com.geolock.app.data.repository

import com.geolock.app.data.local.BlockedAppDao
import com.geolock.app.data.local.BlockedAppEntity
import com.geolock.app.data.local.ZoneBlockedAppDao
import com.geolock.app.data.local.ZoneBlockedAppEntity
import com.geolock.app.data.local.ZoneDao
import com.geolock.app.data.local.ZoneDomainDao
import com.geolock.app.data.local.ZoneDomainEntity
import com.geolock.app.data.local.ZoneEntity
import com.geolock.app.domain.BlockedApp
import com.geolock.app.domain.UnlockMode
import com.geolock.app.domain.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(
    private val zoneDao: ZoneDao,
    private val blockedAppDao: BlockedAppDao,
    private val zoneBlockedAppDao: ZoneBlockedAppDao,
    private val zoneDomainDao: ZoneDomainDao
) {
    val zones: Flow<List<Zone>> = combine(
        zoneDao.observeZones(),
        zoneBlockedAppDao.observeAll(),
        blockedAppDao.observeAll(),
        zoneDomainDao.observeAll()
    ) { zoneEntities, links, appEntities, domainLinks ->
        val apps = appEntities.associateBy { it.packageName }
        zoneEntities.map { zone ->
            val zoneApps = links
                .filter { it.zoneId == zone.id }
                .map { link ->
                    apps[link.packageName]?.toModel() ?: BlockedApp(link.packageName, link.packageName)
                }
            val domains = domainLinks.filter { it.zoneId == zone.id }.map { it.domain }
            zone.toModel(zoneApps, domains)
        }
    }

    suspend fun getZones(): List<Zone> {
        val apps = blockedAppDao.getAll().associateBy { it.packageName }
        val links = zoneBlockedAppDao.getAll()
        val domains = zoneDomainDao.getAll()
        return zoneDao.getZones().map { zone ->
            val zoneApps = links.filter { it.zoneId == zone.id }.map { link ->
                apps[link.packageName]?.toModel() ?: BlockedApp(link.packageName, link.packageName)
            }
            zone.toModel(zoneApps, domains.filter { it.zoneId == zone.id }.map { it.domain })
        }
    }

    suspend fun getZone(id: String): Zone? {
        val entity = zoneDao.getZone(id) ?: return null
        val apps = blockedAppDao.getAll().associateBy { it.packageName }
        val zoneApps = zoneBlockedAppDao.getForZone(id).map { link ->
            apps[link.packageName]?.toModel() ?: BlockedApp(link.packageName, link.packageName)
        }
        val domains = zoneDomainDao.getForZone(id).map { it.domain }
        return entity.toModel(zoneApps, domains)
    }

    suspend fun saveZone(zone: Zone): Zone {
        val id = zone.id.ifBlank { UUID.randomUUID().toString() }
        val stored = zone.copy(id = id, createdAt = if (zone.createdAt == 0L) System.currentTimeMillis() else zone.createdAt)
        zoneDao.upsert(stored.toEntity())
        blockedAppDao.upsertAll(stored.blockedApplications.map { it.toEntity() })
        zoneBlockedAppDao.deleteForZone(id)
        zoneBlockedAppDao.upsertAll(
            stored.blockedApplications.map { ZoneBlockedAppEntity(id, it.packageName) }
        )
        zoneDomainDao.deleteForZone(id)
        zoneDomainDao.upsertAll(stored.blockedDomains.map { ZoneDomainEntity(id, it) })
        return stored
    }

    suspend fun deleteZone(id: String) {
        zoneBlockedAppDao.deleteForZone(id)
        zoneDomainDao.deleteForZone(id)
        zoneDao.delete(id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val zone = getZone(id) ?: return
        zoneDao.upsert(zone.copy(enabled = enabled).toEntity())
    }

    private fun ZoneEntity.toModel(apps: List<BlockedApp>, domains: List<String>) = Zone(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        enabled = enabled,
        createdAt = createdAt,
        locationLabel = locationLabel,
        unlockMode = runCatching { UnlockMode.valueOf(unlockMode) }.getOrDefault(UnlockMode.TEMPORARY),
        unlockDurationMinutes = unlockDurationMinutes,
        blockedApplications = apps,
        blockedDomains = domains
    )

    private fun Zone.toEntity() = ZoneEntity(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        enabled = enabled,
        createdAt = createdAt,
        locationLabel = locationLabel,
        unlockMode = unlockMode.name,
        unlockDurationMinutes = unlockDurationMinutes
    )

    private fun BlockedAppEntity.toModel() = BlockedApp(packageName, appName)
    private fun BlockedApp.toEntity() = BlockedAppEntity(packageName, appName)
}
