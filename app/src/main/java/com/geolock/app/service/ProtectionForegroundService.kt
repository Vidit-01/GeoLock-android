package com.geolock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.geolock.app.MainActivity
import com.geolock.app.R
import com.geolock.app.data.repository.LogRepository
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.domain.ActivityEventType
import com.geolock.app.domain.ProtectionMonitor
import com.geolock.app.domain.ProtectionStatus
import com.geolock.app.location.GeofenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProtectionForegroundService : LifecycleService() {
    @Inject lateinit var protectionMonitor: ProtectionMonitor
    @Inject lateinit var geofenceManager: GeofenceManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var logRepository: LogRepository

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startInForeground(currentNotification("Starting protection…", degraded = false))
        lifecycleScope.launch {
            geofenceManager.refreshGeofences()
            if (settingsRepository.current().protectionEnabled) {
                DnsVpnService.start(this@ProtectionForegroundService)
            }
            protectionMonitor.snapshot.collect { snapshot ->
                val text = when (snapshot.status) {
                    ProtectionStatus.DISABLED -> "Protection is off"
                    ProtectionStatus.ACTIVE -> {
                        val zone = snapshot.activeZones.joinToString(", ") { it.name }.ifBlank { "No active zone" }
                        "$zone · ${snapshot.lockedApps.size} locked apps"
                    }
                    ProtectionStatus.DEGRADED -> snapshot.reasons.firstOrNull() ?: "Protection is degraded"
                }
                val notification = currentNotification(text, snapshot.status != ProtectionStatus.ACTIVE)
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                if (snapshot.status == ProtectionStatus.DEGRADED) {
                    maybeAlert(snapshot.reasons.firstOrNull().orEmpty())
                }
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                val settings = settingsRepository.current()
                if (settings.protectionEnabled) {
                    geofenceManager.evaluateCurrentLocation()
                    if (!protectionMonitor.isAccessibilityEnabled()) {
                        logRepository.log(
                            ActivityEventType.PERMISSION_LOST,
                            "Accessibility Service is disabled"
                        )
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startInForeground(notification: Notification) {
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else -> 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun currentNotification(text: String, degraded: Boolean): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (degraded) getString(R.string.protection_degraded_title) else getString(R.string.protection_active_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(launch)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private var lastAlert: String = ""
    private fun maybeAlert(reason: String) {
        if (reason.isBlank() || reason == lastAlert) return
        lastAlert = reason
        val launch = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.protection_degraded_title))
            .setContentText(reason)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ALERT_ID, notification)
    }

    private fun createChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, getString(R.string.protection_notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, getString(R.string.alert_notification_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 31
        private const val ALERT_ID = 32
        private const val CHANNEL_STATUS = "geolock_status"
        private const val CHANNEL_ALERTS = "geolock_alerts"
        const val ACTION_STOP = "com.geolock.app.STOP_PROTECTION"

        fun start(context: Context) {
            val intent = Intent(context, ProtectionForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            DnsVpnService.stop(context)
            val intent = Intent(context, ProtectionForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
            context.stopService(Intent(context, ProtectionForegroundService::class.java))
        }
    }
}
