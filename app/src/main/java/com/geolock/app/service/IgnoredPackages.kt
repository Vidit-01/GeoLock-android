package com.geolock.app.service

object IgnoredPackages {
    private val exact = setOf(
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.intentresolver",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.sec.android.app.launcher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        "net.oneplus.launcher",
        "com.nothing.launcher",
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod.latin"
    )

    fun shouldIgnore(packageName: String, ownPackage: String): Boolean {
        if (packageName == ownPackage) return true
        if (packageName in exact) return true
        if (packageName.contains("launcher", ignoreCase = true)) return true
        if (packageName.contains("systemui", ignoreCase = true)) return true
        return false
    }
}
