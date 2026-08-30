# GeoLock

Personal geographic app lock for Android. Define zones, pick apps and domains to block, and they stay locked while you are inside.

This is a self-control tool, not parental control or MDM.

## Features

- Geofenced zones with per-zone blocked apps and domains
- Accessibility overlay lock for blocked apps
- Local DNS VPN to sinkhole listed domains inside a zone
- Unlock key stored with Android Keystore (never plaintext)
- Temporary unlock or unlock until you leave the zone
- Device admin and settings lock to make uninstall harder
- Factory-reset screen interception from Android Settings

## Requirements

- Android 10+ (API 29)
- Location, accessibility, and (optional) device admin / VPN permissions

## Build

Open the project in Android Studio, or:

```bash
./gradlew.bat assembleDebug
```

Do not commit `keystore/`, `local.properties`, or APKs. Signing files are local only.
