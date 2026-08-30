package com.geolock.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ZoneEntity::class,
        BlockedAppEntity::class,
        ZoneBlockedAppEntity::class,
        ZoneDomainEntity::class,
        UnlockSessionEntity::class,
        ActivityLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GeoLockDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun zoneBlockedAppDao(): ZoneBlockedAppDao
    abstract fun zoneDomainDao(): ZoneDomainDao
    abstract fun unlockSessionDao(): UnlockSessionDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS zone_domains (zoneId TEXT NOT NULL, domain TEXT NOT NULL, PRIMARY KEY(zoneId, domain))"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_zone_domains_domain ON zone_domains(domain)")
            }
        }
    }
}
