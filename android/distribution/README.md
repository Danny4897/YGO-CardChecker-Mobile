# FOSS in-app updates (YGOChecker Android)

Repo: [Danny4897/YGO-CardChecker-Mobile](https://github.com/Danny4897/YGO-CardChecker-Mobile)

## Feed (do not use jsDelivr `@main` alone)

Primary URL baked into recent builds:

```text
https://github.com/Danny4897/YGO-CardChecker-Mobile/releases/latest/download/update.json
```

The client always probes **multiple** mirrors and keeps the highest `versionCode`:

1. GitHub Release asset (`releases/latest/download/update.json`) — updates instantly on upload
2. GitHub raw (`main` / `feed` branches)
3. jsDelivr `@latest` (semver tags; purgeable)
4. Legacy jsDelivr `@main` (branch CDN can stay stale for hours — never the only source)

**Never** ship a build whose *only* feed is `cdn.jsdelivr.net/...@main/...`. That caused the 0.3.x updater outage.

Override in `android/local.properties` with `UPDATE_FEED_URL=` if needed.

## Each release

1. Bump `versionCode` (+1) and `versionName` in `android/app/build.gradle.kts`
2. `assembleRelease`
3. Create GitHub Release `vX.Y.Z` and attach **both**:
   - `app-release.apk`
   - `android/distribution/update.json`
4. Update `android/distribution/update.json` on branches `main` and `feed`
5. Force-move tag `main` to that commit (legacy clients hit `@main`):
   `git tag -f main && git push origin refs/tags/main --force`
6. Purge jsDelivr **@latest** (not only @main):
   `https://purge.jsdelivr.net/gh/Danny4897/YGO-CardChecker-Mobile@latest/android/distribution/update.json`

`versionCode` must be **greater** than what users already have. Keep the **same signing key** across updates.

### Play Protect

Sideloaded FOSS apps are scanned by Play Protect on every APK install/update. Shipping non-debuggable release builds + a stable signing cert is the usual FOSS approach.
