package com.geolock.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geolock.app.domain.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "geolock_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.ONBOARDING] = next.onboardingComplete
            prefs[Keys.PROTECTION] = next.protectionEnabled
            prefs[Keys.UNLOCK_MINUTES] = next.unlockDurationMinutes
            prefs[Keys.REQUIRE_KEY] = next.requireKeyEveryTime
            prefs[Keys.DEFAULT_RADIUS] = next.defaultRadiusMeters
            prefs[Keys.LOCATION_UNCERTAIN] = next.locationUncertain
            prefs[Keys.GEOFENCES] = next.geofencesRegistered
            prefs[Keys.LAST_LOCATION] = next.lastLocationEvent
            prefs[Keys.LAST_FOREGROUND] = next.lastForegroundApp
            prefs[Keys.LAST_BLOCKED] = next.lastBlockedAttempt
            prefs[Keys.ACTIVE_ZONES] = next.activeZoneIds.joinToString(",")
        }
    }

    suspend fun setUnlockKeyMaterial(salt: String, iv: String, ciphertext: String) {
        dataStore.edit {
            it[Keys.KEY_SALT] = salt
            it[Keys.KEY_IV] = iv
            it[Keys.KEY_CIPHER] = ciphertext
        }
    }

    val unlockKeyMaterial: Flow<UnlockKeyMaterial?> = dataStore.data.map { prefs ->
        val salt = prefs[Keys.KEY_SALT]
        val iv = prefs[Keys.KEY_IV]
        val cipher = prefs[Keys.KEY_CIPHER]
        if (salt.isNullOrBlank() || iv.isNullOrBlank() || cipher.isNullOrBlank()) {
            null
        } else {
            UnlockKeyMaterial(salt, iv, cipher)
        }
    }

    data class UnlockKeyMaterial(
        val salt: String,
        val iv: String,
        val ciphertext: String
    )

    private fun Preferences.toSettings(): AppSettings {
        val active = this[Keys.ACTIVE_ZONES]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        return AppSettings(
            onboardingComplete = this[Keys.ONBOARDING] ?: false,
            protectionEnabled = this[Keys.PROTECTION] ?: false,
            unlockDurationMinutes = this[Keys.UNLOCK_MINUTES] ?: 5,
            requireKeyEveryTime = this[Keys.REQUIRE_KEY] ?: false,
            defaultRadiusMeters = this[Keys.DEFAULT_RADIUS] ?: 150f,
            locationUncertain = this[Keys.LOCATION_UNCERTAIN] ?: false,
            geofencesRegistered = this[Keys.GEOFENCES] ?: false,
            lastLocationEvent = this[Keys.LAST_LOCATION] ?: "",
            lastForegroundApp = this[Keys.LAST_FOREGROUND] ?: "",
            lastBlockedAttempt = this[Keys.LAST_BLOCKED] ?: "",
            activeZoneIds = active
        )
    }

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val PROTECTION = booleanPreferencesKey("protection_enabled")
        val UNLOCK_MINUTES = intPreferencesKey("unlock_duration_minutes")
        val REQUIRE_KEY = booleanPreferencesKey("require_key_every_time")
        val DEFAULT_RADIUS = floatPreferencesKey("default_radius")
        val LOCATION_UNCERTAIN = booleanPreferencesKey("location_uncertain")
        val GEOFENCES = booleanPreferencesKey("geofences_registered")
        val LAST_LOCATION = stringPreferencesKey("last_location_event")
        val LAST_FOREGROUND = stringPreferencesKey("last_foreground_app")
        val LAST_BLOCKED = stringPreferencesKey("last_blocked_attempt")
        val ACTIVE_ZONES = stringPreferencesKey("active_zone_ids")
        val KEY_SALT = stringPreferencesKey("unlock_key_salt")
        val KEY_IV = stringPreferencesKey("unlock_key_iv")
        val KEY_CIPHER = stringPreferencesKey("unlock_key_cipher")
    }
}
