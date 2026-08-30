package com.geolock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.geolock.app.data.repository.LogRepository
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.domain.ActivityEventType
import com.geolock.app.domain.AppCatalog
import com.geolock.app.domain.LockManager
import com.geolock.app.ui.lock.LockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class GeoLockAccessibilityService : AccessibilityService() {
    @Inject lateinit var lockManager: LockManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var logRepository: LogRepository
    @Inject lateinit var appCatalog: AppCatalog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null
    private var currentForeground: String? = null
    private var lastLockShownAt = 0L
    private var lastLockedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            logRepository.log(ActivityEventType.SERVICE_STARTED, "Accessibility service connected")
            lockManager.notifyChanged()
        }
        observeJob?.cancel()
        observeJob = scope.launch {
            lockManager.lockStateChanged.collectLatest {
                currentForeground?.let { evaluateAndLock(it) }
            }
        }
        ProtectionForegroundService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (IgnoredPackages.shouldIgnore(packageName, this.packageName)) return
        currentForeground = packageName
        scope.launch {
            val settings = settingsRepository.current()
            if (settings.protectionEnabled && ResetScreenDetector.isResetScreen(rootInActiveWindow)) {
                showResetBlock()
                return@launch
            }
            settingsRepository.setLastForegroundApp(appCatalog.appName(packageName))
            evaluateAndLock(packageName)
        }
    }

    private suspend fun evaluateAndLock(packageName: String) {
        val settings = settingsRepository.current()
        if (!settings.protectionEnabled) return
        val verdict = lockManager.evaluate(packageName)
        if (!verdict.locked) return

        val now = System.currentTimeMillis()
        if (packageName == lastLockedPackage && now - lastLockShownAt < 750) return
        lastLockedPackage = packageName
        lastLockShownAt = now

        val appName = appCatalog.appName(packageName)
        val zoneNames = verdict.blockingZones.joinToString(", ") { it.name }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        settingsRepository.setLastBlockedAttempt("$appName — $time")
        logRepository.log(
            type = ActivityEventType.APP_BLOCKED,
            message = "$appName blocked",
            packageName = packageName,
            appName = appName,
            zoneId = verdict.blockingZones.firstOrNull()?.id,
            zoneName = verdict.blockingZones.firstOrNull()?.name
        )

        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(LockActivity.EXTRA_PACKAGE, packageName)
            putExtra(LockActivity.EXTRA_APP_NAME, appName)
            putExtra(LockActivity.EXTRA_ZONE_NAMES, zoneNames)
            putExtra(LockActivity.EXTRA_ZONE_ID, verdict.blockingZones.firstOrNull()?.id.orEmpty())
            putExtra(
                LockActivity.EXTRA_UNLOCK_MINUTES,
                verdict.blockingZones.firstOrNull()?.unlockDurationMinutes
                    ?: settings.unlockDurationMinutes
            )
            putExtra(
                LockActivity.EXTRA_UNLOCK_MODE,
                (verdict.blockingZones.firstOrNull()?.unlockMode
                    ?: com.geolock.app.domain.UnlockMode.TEMPORARY).name
            )
            putExtra(LockActivity.EXTRA_SYSTEM_LOCK, verdict.systemLock)
        }
        startActivity(intent)
    }

    private fun showResetBlock() {
        val now = System.currentTimeMillis()
        if (lastLockedPackage == RESET_LOCK && now - lastLockShownAt < 1000) return
        lastLockedPackage = RESET_LOCK
        lastLockShownAt = now
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(LockActivity.EXTRA_RESET_BLOCKED, true)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        scope.launch {
            logRepository.log(
                ActivityEventType.PROTECTION_DEGRADED,
                "Accessibility service disconnected"
            )
        }
        return super.onUnbind(intent)
    }

    companion object {
        private const val RESET_LOCK = "__factory_reset__"
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
