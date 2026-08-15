# FOSS in-app updates (YGOChecker Android)

Repo: [Danny4897/YGO-CardChecker-Mobile](https://github.com/Danny4897/YGO-CardChecker-Mobile)

The app checks this feed on launch (and from Settings → Updates):

```text
https://raw.githubusercontent.com/Danny4897/YGO-CardChecker-Mobile/main/android/distribution/update.json
```

(Default is baked into `BuildConfig.UPDATE_FEED_URL`. Override in `android/local.properties` if needed.)

## Each release

1. Bump in `android/app/build.gradle.kts`:
   - `versionCode` (+1)
   - `versionName` (e.g. `0.2.0`)
2. Build the APK (`assembleDebug` or `assembleRelease`).
3. Create a **GitHub Release** tag `v0.2.0` and attach the APK (e.g. `app-debug.apk` / `app-release.apk`).
4. Update `android/distribution/update.json` on `main`:

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "https://github.com/Danny4897/YGO-CardChecker-Mobile/releases/download/v0.2.0/app-debug.apk",
  "changelog": "What changed…"
}
```

5. Push `main` so raw.githubusercontent.com serves the new JSON.

`versionCode` must be **greater** than what users already have. Prefer the same signing key across updates (debug↔release do not upgrade each other).

## User flow

1. Open app → after splash, GET `update.json`.
2. Dialog: Download & install / Later.
3. First time, Android may ask to allow installs from YGOChecker.
