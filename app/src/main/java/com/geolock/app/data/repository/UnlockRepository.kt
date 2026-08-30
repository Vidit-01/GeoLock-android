package com.geolock.app.data.repository

import com.geolock.app.data.local.UnlockSessionDao
import com.geolock.app.data.local.UnlockSessionEntity
import com.geolock.app.domain.ProtectedPackages
import com.geolock.app.domain.UnlockMode
import com.geolock.app.domain.UnlockSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnlockRepository @Inject constructor(
    private val dao: UnlockSessionDao
) {
    val sessions: Flow<List<UnlockSession>> = dao.observeAll().map { rows ->
        rows.map { it.toModel() }
    }

    suspend fun getSessions(): List<UnlockSession> = dao.getAll().map { it.toModel() }

    suspend fun grantTemporary(packageName: String, durationMinutes: Int): UnlockSession {
        val now = System.currentTimeMillis()
        val session = UnlockSession(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            zoneId = null,
            mode = UnlockMode.TEMPORARY,
            expiresAt = now + durationMinutes * 60_000L,
            createdAt = now
        )
        dao.upsert(session.toEntity())
        return session
    }

    suspend fun grantSettingsUnlock(durationMinutes: Int): UnlockSession {
        return grantTemporary(ProtectedPackages.SETTINGS_UNLOCK, durationMinutes)
    }

    suspend fun grantAppUnlock(): UnlockSession {
        return grantTemporary(ProtectedPackages.APP_UNLOCK, 60)
    }

    suspend fun hasAppUnlock(): Boolean {
        clearExpired()
        val now = System.currentTimeMillis()
        return getSessions().any { session ->
            session.packageName == ProtectedPackages.APP_UNLOCK &&
                (session.expiresAt == null || session.expiresAt > now)
        }
    }

    suspend fun clearAppUnlock() {
        getSessions()
            .filter { it.packageName == ProtectedPackages.APP_UNLOCK }
            .forEach { dao.delete(it.id) }
    }

    suspend fun hasSettingsUnlock(): Boolean {
        clearExpired()
        val now = System.currentTimeMillis()
        return getSessions().any { session ->
            session.packageName == ProtectedPackages.SETTINGS_UNLOCK &&
                (session.expiresAt == null || session.expiresAt > now)
        }
    }

    suspend fun grantZoneUnlock(zoneId: String): UnlockSession {
        val now = System.currentTimeMillis()
        val session = UnlockSession(
            id = "zone-$zoneId",
            packageName = null,
            zoneId = zoneId,
            mode = UnlockMode.UNTIL_LEAVE_ZONE,
            expiresAt = null,
            createdAt = now
        )
        dao.upsert(session.toEntity())
        return session
    }

    suspend fun clearExpired() {
        dao.deleteExpired(System.currentTimeMillis())
    }

    suspend fun clearZoneUnlocks(zoneId: String) {
        dao.clearZoneUnlocks(zoneId)
    }

    private fun UnlockSessionEntity.toModel() = UnlockSession(
        id = id,
        packageName = packageName,
        zoneId = zoneId,
        mode = runCatching { UnlockMode.valueOf(mode) }.getOrDefault(UnlockMode.TEMPORARY),
        expiresAt = expiresAt,
        createdAt = createdAt
    )

    private fun UnlockSession.toEntity() = UnlockSessionEntity(
        id = id,
        packageName = packageName,
        zoneId = zoneId,
        mode = mode.name,
        expiresAt = expiresAt,
        createdAt = createdAt
    )
}
