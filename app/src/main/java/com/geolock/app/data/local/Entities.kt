package com.geolock.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val enabled: Boolean,
    val createdAt: Long,
    val locationLabel: String,
    val unlockMode: String,
    val unlockDurationMinutes: Int
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String
)

@Entity(
    tableName = "zone_blocked_apps",
    primaryKeys = ["zoneId", "packageName"],
    indices = [Index("packageName")]
)
data class ZoneBlockedAppEntity(
    val zoneId: String,
    val packageName: String
)

@Entity(
    tableName = "zone_domains",
    primaryKeys = ["zoneId", "domain"],
    indices = [Index("domain")]
)
data class ZoneDomainEntity(
    val zoneId: String,
    val domain: String
)

@Entity(tableName = "unlock_sessions")
data class UnlockSessionEntity(
    @PrimaryKey val id: String,
    val packageName: String?,
    val zoneId: String?,
    val mode: String,
    val expiresAt: Long?,
    val createdAt: Long
)

@Entity(
    tableName = "activity_logs",
    indices = [Index("timestamp")]
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val packageName: String?,
    val appName: String?,
    val zoneId: String?,
    val zoneName: String?,
    val message: String
)
