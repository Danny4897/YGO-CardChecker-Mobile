# YGOChecker Android

Native, phone-first Android client built with Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Coroutines/Flow, Coil, Retrofit and OkHttp.

## Requirements

- JDK 17
- Android Studio / Android SDK 36
- `ANDROID_HOME` (or `local.properties` with `sdk.dir=...`)

## Build

```powershell
cd android
./gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Run parser tests with `./gradlew.bat :data:deck:testDebugUnitTest`.

## Architecture

- `core:model`, `core:common`: platform-neutral values and railway-style results
- `core:domain`: repository ports and use-case contracts/implementations
- `data:cards`, `data:deck`: Room/DataStore adapters and import/export codecs
- `feature:*`: Compose UI depending only on core modules
- `app`: Hilt wiring, databases and bottom-navigation shell

The bundled catalog intentionally contains a small offline seed. Images use the YGOPRODeck CDN and may be absent offline.
