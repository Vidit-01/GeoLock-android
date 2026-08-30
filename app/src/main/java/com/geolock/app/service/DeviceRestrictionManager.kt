package com.geolock.app.service

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager

object DeviceRestrictionManager {
    fun apply(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = GeoLockDeviceAdminReceiver.component(context)
        if (!dpm.isDeviceOwnerApp(context.packageName) && !dpm.isProfileOwnerApp(context.packageName)) {
            return
        }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER) }
    }

    fun isFactoryResetBlocked(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return dpm.isDeviceOwnerApp(context.packageName)
    }
}
