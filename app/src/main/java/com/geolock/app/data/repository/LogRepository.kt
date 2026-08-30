package com.geolock.app.data.repository

import com.geolock.app.data.local.ActivityLogDao
import com.geolock.app.data.local.ActivityLogEntity
import com.geolock.app.domain.ActivityEventType
import com.geolock.app.domain.ActivityLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val dao: ActivityLogDao
) {
    val recent: Flow<List<ActivityLogEntry>> = dao.observeRecent().map { rows ->
        rows.map { it.toModel() }
    }

    suspend fun log(
        type: ActivityEventType,
        message: String,
        packageName: String? = null,
        appName: String? = null,
        zoneId: String? = null,
        zoneName: String? = null
    ) {
        dao.insert(
            ActivityLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = type.name,
                packageName = packageName,
                appName = appName,
                zoneId = zoneId,
                zoneName = zoneName,
                message = message
            )
        )
        dao.prune()
    }

    private fun ActivityLogEntity.toModel() = ActivityLogEntry(
        id = id,
        timestamp = timestamp,
        eventType = runCatching { ActivityEventType.valueOf(eventType) }
            .getOrDefault(ActivityEventType.SERVICE_STARTED),
        packageName = packageName,
        appName = appName,
        zoneId = zoneId,
        zoneName = zoneName,
        message = message
    )
}
