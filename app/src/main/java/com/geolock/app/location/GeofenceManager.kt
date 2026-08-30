package com.geolock.app.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import com.geolock.app.data.repository.LogRepository
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.ZoneRepository
import com.geolock.app.domain.ActivityEventType
import com.geolock.app.domain.LockManager
import com.geolock.app.domain.ProtectionMonitor
import com.geolock.app.domain.Zone
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zoneRepository: ZoneRepository,
    private val lockManager: LockManager,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val protectionMonitor: ProtectionMonitor
) {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun refreshGeofences() {
        if (!protectionMonitor.hasFineLocation()) {
            settingsRepository.setGeofencesRegistered(false)
            return
        }

        val enabled = zoneRepository.getZones().filter { it.enabled }
        runCatching { client.removeGeofences(pendingIntent).await() }

        if (enabled.isEmpty()) {
            settingsRepository.setGeofencesRegistered(true)
            lockManager.replaceActiveZones(emptySet())
            return
        }

        val geofences = enabled.map { zone ->
            Geofence.Builder()
                .setRequestId(zone.id)
                .setCircularRegion(zone.latitude, zone.longitude, zone.radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT or
                        Geofence.GEOFENCE_TRANSITION_DWELL
                )
                .setLoiteringDelay(30_000)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()

        try {
            client.addGeofences(request, pendingIntent).await()
            settingsRepository.setGeofencesRegistered(true)
        } catch (error: Exception) {
            settingsRepository.setGeofencesRegistered(false)
            logRepository.log(
                ActivityEventType.PROTECTION_DEGRADED,
                "Could not register geofences: ${error.message ?: "unknown error"}"
            )
        }

        evaluateCurrentLocation(enabled)
    }

    @SuppressLint("MissingPermission")
    suspend fun evaluateCurrentLocation(zones: List<Zone>? = null) {
        val enabledZones = zones ?: zoneRepository.getZones().filter { it.enabled }
        if (!protectionMonitor.hasFineLocation()) return
        if (!protectionMonitor.locationServicesOn()) {
            settingsRepository.setLocationUncertain(true)
            logRepository.log(
                ActivityEventType.LOCATION_UNCERTAIN,
                "Location services are off. Keeping last known zone state."
            )
            return
        }

        val location = currentLocation()
        if (location == null) {
            settingsRepository.setLocationUncertain(true)
            logRepository.log(
                ActivityEventType.LOCATION_UNCERTAIN,
                "Current location is unavailable. Keeping last known zone state."
            )
            return
        }

        settingsRepository.setLocationUncertain(false)
        val inside = enabledZones.filter { zone ->
            distanceMeters(location.latitude, location.longitude, zone.latitude, zone.longitude) <= zone.radius
        }.map { it.id }.toSet()
        lockManager.replaceActiveZones(inside)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        fused.lastLocation.await()?.let { return it }
        return try {
            val token = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun handleTransition(transition: Int, zoneIds: List<String>) {
        val zones = zoneRepository.getZones().associateBy { it.id }
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> {
                settingsRepository.setLocationUncertain(false)
                zoneIds.forEach { id ->
                    val zone = zones[id]
                    lockManager.enterZone(id)
                    settingsRepository.setLastLocationEvent("ENTER ${zone?.name ?: id}")
                    logRepository.log(
                        type = ActivityEventType.ZONE_ENTER,
                        message = "Entered ${zone?.name ?: "zone"}",
                        zoneId = id,
                        zoneName = zone?.name
                    )
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                settingsRepository.setLocationUncertain(false)
                zoneIds.forEach { id ->
                    val zone = zones[id]
                    lockManager.exitZone(id)
                    settingsRepository.setLastLocationEvent("EXIT ${zone?.name ?: id}")
                    logRepository.log(
                        type = ActivityEventType.ZONE_EXIT,
                        message = "Exited ${zone?.name ?: "zone"}",
                        zoneId = id,
                        zoneName = zone?.name
                    )
                }
            }
        }
    }

    companion object {
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val earth = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (earth * c).toFloat()
        }
    }
}
