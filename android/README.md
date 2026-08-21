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
- `data:social`: Supabase (Postgres + Auth + Realtime) adapter for friends, public decks, chat, DMs
- `feature:*`: Compose UI depending only on core modules
- `app`: Hilt wiring, databases and bottom-navigation shell

The bundled catalog intentionally contains a small offline seed. Images use the YGOPRODeck CDN and may be absent offline.

## Social backend (Supabase)

Social features (friends, public decks, chat, DMs) run against the hosted
`ygochecker` Supabase project (Postgres + Auth + Realtime, project ref
`ubflewrwtpbrbkjdohfx`). The URL and anon key are baked in as defaults in
`app/build.gradle.kts`; override them in `android/local.properties` only to
point a build at a different project (e.g. a staging environment):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=sb_publishable_...
```

The anon key is meant to be public — every table is protected by row-level
security instead. See `android/data/social` for the client and
`android/backend/README.md` for the deprecated predecessor this replaced.

One manual setup step outside the codebase: in the Supabase dashboard under
**Authentication → URL Configuration**, add `ygochecker://oauth/magiclink` to
the allowed redirect URLs, and enable **Anonymous sign-ins** under
**Authentication → Providers** — both are dashboard-only settings with no
Supabase MCP/API equivalent, so they weren't set as part of this change.
