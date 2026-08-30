package com.geolock.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY createdAt DESC")
    fun observeZones(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones ORDER BY createdAt DESC")
    suspend fun getZones(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getZone(id: String): ZoneEntity?

    @Query("SELECT * FROM zones WHERE enabled = 1")
    suspend fun getEnabledZones(): List<ZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(zone: ZoneEntity)

    @Update
    suspend fun update(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAll(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<BlockedAppEntity>)
}

@Dao
interface ZoneBlockedAppDao {
    @Query("SELECT * FROM zone_blocked_apps")
    fun observeAll(): Flow<List<ZoneBlockedAppEntity>>

    @Query("SELECT * FROM zone_blocked_apps")
    suspend fun getAll(): List<ZoneBlockedAppEntity>

    @Query("SELECT * FROM zone_blocked_apps WHERE zoneId = :zoneId")
    suspend fun getForZone(zoneId: String): List<ZoneBlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: ZoneBlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<ZoneBlockedAppEntity>)

    @Query("DELETE FROM zone_blocked_apps WHERE zoneId = :zoneId")
    suspend fun deleteForZone(zoneId: String)

    @Query("DELETE FROM zone_blocked_apps WHERE zoneId = :zoneId AND packageName = :packageName")
    suspend fun delete(zoneId: String, packageName: String)
}

@Dao
interface ZoneDomainDao {
    @Query("SELECT * FROM zone_domains")
    fun observeAll(): Flow<List<ZoneDomainEntity>>

    @Query("SELECT * FROM zone_domains")
    suspend fun getAll(): List<ZoneDomainEntity>

    @Query("SELECT * FROM zone_domains WHERE zoneId = :zoneId")
    suspend fun getForZone(zoneId: String): List<ZoneDomainEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<ZoneDomainEntity>)

    @Query("DELETE FROM zone_domains WHERE zoneId = :zoneId")
    suspend fun deleteForZone(zoneId: String)
}

@Dao
interface UnlockSessionDao {
    @Query("SELECT * FROM unlock_sessions")
    fun observeAll(): Flow<List<UnlockSessionEntity>>

    @Query("SELECT * FROM unlock_sessions")
    suspend fun getAll(): List<UnlockSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: UnlockSessionEntity)

    @Query("DELETE FROM unlock_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM unlock_sessions WHERE zoneId = :zoneId AND mode = 'UNTIL_LEAVE_ZONE'")
    suspend fun clearZoneUnlocks(zoneId: String)

    @Query("DELETE FROM unlock_sessions WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM unlock_sessions")
    suspend fun clearAll()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ActivityLogEntity>>

    @Insert
    suspend fun insert(entry: ActivityLogEntity)

    @Query("DELETE FROM activity_logs WHERE id NOT IN (SELECT id FROM activity_logs ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun prune(keep: Int = 500)

    @Query("DELETE FROM activity_logs")
    suspend fun clear()
}

