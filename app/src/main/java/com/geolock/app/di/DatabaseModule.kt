package com.geolock.app.di

import android.content.Context
import androidx.room.Room
import com.geolock.app.data.local.ActivityLogDao
import com.geolock.app.data.local.BlockedAppDao
import com.geolock.app.data.local.GeoLockDatabase
import com.geolock.app.data.local.UnlockSessionDao
import com.geolock.app.data.local.ZoneBlockedAppDao
import com.geolock.app.data.local.ZoneDao
import com.geolock.app.data.local.ZoneDomainDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GeoLockDatabase {
        return Room.databaseBuilder(context, GeoLockDatabase::class.java, "geolock.db")
            .addMigrations(GeoLockDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun zoneDomainDao(db: GeoLockDatabase): ZoneDomainDao = db.zoneDomainDao()

    @Provides
    fun zoneDao(db: GeoLockDatabase): ZoneDao = db.zoneDao()

    @Provides
    fun blockedAppDao(db: GeoLockDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    fun zoneBlockedAppDao(db: GeoLockDatabase): ZoneBlockedAppDao = db.zoneBlockedAppDao()

    @Provides
    fun unlockSessionDao(db: GeoLockDatabase): UnlockSessionDao = db.unlockSessionDao()

    @Provides
    fun activityLogDao(db: GeoLockDatabase): ActivityLogDao = db.activityLogDao()
}
