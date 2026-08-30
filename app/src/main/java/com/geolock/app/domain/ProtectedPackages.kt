package com.geolock.app.domain

object ProtectedPackages {
    const val SETTINGS_UNLOCK = "__geolock_settings__"
    const val APP_UNLOCK = "__geolock_app__"

    private val exact = setOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.nothing.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    fun isProtected(packageName: String): Boolean {
        if (packageName in exact) return true
        if (packageName.contains("packageinstaller", ignoreCase = true)) return true
        if (packageName.endsWith(".settings") || packageName.contains(".settings.")) return true
        return false
    }
}

object DomainNames {
    fun normalize(raw: String): String {
        return raw.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
            .removePrefix("www.")
            .trim('.')
    }

    fun matches(host: String, blocked: Set<String>): Boolean {
        val name = normalize(host)
        if (name.isBlank()) return false
        return blocked.any { domain ->
            name == domain || name.endsWith(".$domain")
        }
    }
}
