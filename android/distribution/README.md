# FOSS in-app updates (YGOChecker Android)

Repo: [Danny4897/YGO-CardChecker-Mobile](https://github.com/Danny4897/YGO-CardChecker-Mobile)

The app checks this feed on launch (and from Settings → Updates):

```text
https://cdn.jsdelivr.net/gh/Danny4897/YGO-CardChecker-Mobile@latest/android/distribution/update.json
```

Always use **`@latest`** (semver GitHub Releases), never `@main` — jsDelivr branch refs can stay stale for up to ~12 hours and purge is unreliable.

The client also probes raw GitHub, `releases/latest/download/update.json`, and legacy `@main`, then keeps the **highest** `versionCode`.

Override in `android/local.properties` with `UPDATE_FEED_URL=` if needed.

## Each release

1. Bump in `android/app/build.gradle.kts`:
   - `versionCode` (+1)
   - `versionName` (e.g. `0.2.2`)
2. Build the **release** APK (`assembleRelease`) — not debug. Debug APKs are `debuggable=true` and Play Protect flags them every time.
3. Create a **GitHub Release** tag `v0.2.2` and attach **both** `app-release.apk` and `android/distribution/update.json` (same JSON as below).
4. Update `android/distribution/update.json` on `main` (and ensure the release tag includes that file so `@latest` serves it):

```json
{
  "versionCode": 4,
  "versionName": "0.2.2",
  "apkUrl": "https://github.com/Danny4897/YGO-CardChecker-Mobile/releases/download/v0.2.2/app-release.apk",
  "changelog": "What changed…"
}
```

5. Push `main`, then purge **`@latest`** (not `@main`):
   `https://purge.jsdelivr.net/gh/Danny4897/YGO-CardChecker-Mobile@latest/android/distribution/update.json`

`versionCode` must be **greater** than what users already have. Keep the **same signing key** across updates (release currently reuses the Android debug keystore so upgrades from early GitHub builds still work).

### Play Protect

Sideloaded FOSS apps are scanned by Play Protect on **every** APK install/update — that is OS policy, not something the app can fully disable. Shipping **non-debuggable release** builds + a stable signing cert is what most FOSS apps do so Protect stops treating the developer as brand-new. Users can tap **Don't send** on the analysis prompt; it should not block the install.

## OAuth (Discord / Google)

Optional. Without credentials the app opens the provider and asks for a **public ID** (manual confirm).

In `android/local.properties` (never commit secrets):

```properties
DISCORD_CLIENT_ID=...
DISCORD_CLIENT_SECRET=...
GOOGLE_CLIENT_ID=...
```

Register these redirect URIs with the providers:

- Discord: `ygochecker://oauth/discord`
- Google (OAuth Android / Web client with custom URI): `ygochecker://oauth/google`

Discord Developer Portal checklist:

1. Create an Application → OAuth2
2. Add redirect `ygochecker://oauth/discord`
3. Copy **Client ID** + **Client Secret** into `local.properties`
4. Rebuild the APK (credentials are baked into `BuildConfig`)

Discord needs both client id **and** secret for on-device token exchange (confidential client). Google works with a public client + PKCE when the redirect URI is allow-listed.

Without credentials, tapping Discord/Google shows a clear error instead of a fake login screen.

## Social API (v0.3.0+)

Friends / public decks / chat require the SQLite micro-backend on a host PC:

```bash
cd android/backend
npm run dev
```

Copy the printed `https://….trycloudflare.com` URL into the app: **Settings → Social API**.
Keep the PC online while testers use social features. See `android/backend/README.md`.

## User flow

1. Open app → after splash, GET `update.json`.
2. Dialog: Download & install / Later.
3. First time, Android may ask to allow installs from YGOChecker.
