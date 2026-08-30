package com.geolock.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class GeocodedPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

@Singleton
class DeviceLocation @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        fused.lastLocation.await()?.let { return it }
        return try {
            val token = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun reverse(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.getOrNull().orEmpty()
    }

    suspend fun search(query: String): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocationName(query, 5)
                ?.mapNotNull { address ->
                    val lat = address.latitude
                    val lng = address.longitude
                    GeocodedPlace(
                        label = address.getAddressLine(0) ?: query,
                        latitude = lat,
                        longitude = lng
                    )
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }
}
