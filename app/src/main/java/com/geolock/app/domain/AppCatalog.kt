package com.geolock.app.domain

import android.content.Intent
import android.content.pm.PackageManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCatalog @Inject constructor(
    private val packageManager: PackageManager
) {
    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { resolve ->
                InstalledApp(
                    packageName = resolve.activityInfo.packageName,
                    appName = resolve.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    fun appName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun isOwnPackage(packageName: String, ownPackage: String): Boolean = packageName == ownPackage
}
