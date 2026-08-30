package com.geolock.app.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class GeoLockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        DeviceRestrictionManager.apply(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling this lets you uninstall GeoLock and can allow a factory reset. Unlock GeoLock with your key first if you meant to do that."
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, GeoLockDeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val manager = context.getSystemService(DevicePolicyManager::class.java)
            return manager.isAdminActive(component(context))
        }

        fun enableIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops GeoLock from being uninstalled until you turn this off. You will still need your unlock key to open Settings."
                )
        }
    }
}
