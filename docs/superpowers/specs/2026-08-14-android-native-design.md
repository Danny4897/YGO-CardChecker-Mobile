# Android Native YGOChecker — Design Spec

**Date:** 2026-08-14  
**Branch:** `refactor/android-native`  
**Status:** Draft for review  
**Related web app:** `ygo-card-checker/` (Angular 20)

## 1. Goal

Ship a **native Kotlin Android** application that covers the **core duelist loop** of YGOChecker with production-grade architecture: multi-module, testable, single-responsibility boundaries. Deliver an **internally installable APK** (not Play Store launch in v1).

## 2. Decisions (locked)

| Topic | Choice |
|-------|--------|
| Platform | Native Kotlin + Jetpack Compose |
| UI | Material 3, Android-idiomatic navigation, **duel brand** colors/typography inspired by web |
| v1 scope | Search + decklists + format legality + import/export (text + YDKE) |
| Deferred | Assist synergies, Combo, Overlay OCR, YgoFlow |
| Data | Hybrid: local catalog (Room) + remote images + banlist sync; **future** full offline pack |
| Distribution | Debug/Release APK for internal sideload |
| Repo layout | `YGOChecker/android/` — Gradle root separate from Angular |
| Architecture | Multi-module Clean Architecture |

## 3. Non-goals (v1)

- Play Store listing, billing, accounts/auth
- Pixel-perfect clone of DaisyUI web layouts
- Full offline download pack (only stub/settings hook)
- Sharing runtime with Angular (parity of **behavior/contracts**, not shared binary)

## 4. Module map (SRP)

```
android/
  settings.gradle.kts
  build.gradle.kts
  app/                      # APK entry, Hilt, NavHost
  core/
    model/                  # Immutable domain types
    domain/                 # Use cases + repository ports (interfaces)
    common/                 # Result, errors, dispatchers
    designsystem/           # Theme + shared Compose components
  data/
    cards/                  # Room catalog, API sync, image URL resolution
    deck/                   # Deck persistence, text/YDKE import-export
  feature/
    search/
    decklist/
    legality/               # May be thin if legality is embedded in editor; still own use cases
    settings/
```

### Dependency rules

1. `feature:*` → `core:domain`, `core:model`, `core:designsystem`, `core:common` only.  
2. `data:*` implements ports defined in `core:domain`.  
3. `feature:*` **must not** depend on `data:*`.  
4. `app` depends on features + data modules and performs DI binding.  
5. No circular dependencies. `core:model` has no Android UI / Room / Retrofit.

### Responsibility cheatsheet

| Module | Owns | Does not own |
|--------|------|--------------|
| `core:model` | Card, Decklist, Format, BanStatus | Persistence, UI |
| `core:domain` | Use cases, repository interfaces | Room entities, Compose |
| `data:cards` | Catalog DB, network sync | Deck editing UI |
| `data:deck` | Deck CRUD, parsers | Card search ranking |
| `feature:search` | Search UI + VM | Writing deck files |
| `feature:decklist` | List + editor UI + VM | Banlist HTTP |
| `feature:settings` | Format/lang preferences | Card queries |

## 5. Domain model (parity with web contracts)

### Cards

- `id` (passcode), `name`, `type`, `race`, `attribute`, `atk`/`def`/`level` (optional), `desc`, image URLs  
- Format playability / max copies derived from banlist index (same semantics as web `format-legality`)

### Decklist

- `id`, `name`, `updatedAt`, `cards[]` with `quantity`, `section` ∈ {main, extra, side}  
- Max copies from banlist status (0/1/2/3)  
- Extra deck placement by monster type rules (align with web `resolveDeckSection`)

### Formats (v1)

- `goat`, `edison`, `hat`, `tengu`, `tcg` — single global selection (DataStore), same idea as web `FormatStore`

### Import / export

- **Text:** `qty name` lines; optional `#main` `#extra` `#side`  
- **YDKE:** `ydke://main!extra!side!` passcode-based  
- Failures as `Result.Err` with stable `errorKey` (i18n), no exceptions for business flow

## 6. Use cases (v1)

`core:domain` examples (one class ≈ one action):

- `SearchCards`  
- `GetCardDetails`  
- `ListDecklists` / `CreateDecklist` / `RenameDecklist` / `DeleteDecklist`  
- `GetDecklist` / `AddCardToDeck` / `RemoveCardFromDeck` / `SetCardQuantity` / `MoveCardSection`  
- `EvaluateDeckLegality` (per card + deck summary)  
- `ImportDeckFromText` / `ExportDeckToText`  
- `ImportDeckFromYdke` / `ExportDeckToYdke`  
- `GetSelectedFormat` / `SetSelectedFormat`  
- `SyncBanlist` / `EnsureCatalogReady`  

Future (interfaces reserved, not implemented in v1): `PrepareOfflinePack`.

## 7. Data layer

### Catalog (`data:cards`)

- **Room** tables: cards, format_legality (or playable map), meta/sync watermark  
- Bootstrap: ship a **reasonable seed** (subset or full export derived from existing knowledge pipeline) so first launch works offline for search/legality of seeded cards  
- Sync: pull updates when online (YGOPRODeck and/or packaged JSON assets mirrored from web)  
- Images: Coil + HTTPS CDN (ygoprodeck); cache on disk  

### Decks (`data:deck`)

- Room (or DataStore+JSON for v1 if fewer queries — **prefer Room** for consistency)  
- Parsers pure in `data:deck` or `core:domain` helpers tested without Android  

### Preferences

- DataStore: format id, language (`it`/`en`), future offline-pack flag  

## 8. UI / UX (Material 3)

- Edge-to-edge, dynamic color optional but brand tokens override (duel palette)  
- Bottom navigation: **Search** | **Decks** | **Settings**  
- Deck editor: section tabs (Main / Extra / Side), quantity steppers, legality badges  
- Search: query field, result list with small art + quick-add to active deck  
- Empty states and error banners with translated `errorKey`  
- Italian + English strings (resources), default from system locale with override in Settings  

## 9. Tech stack

- AGP + Kotlin current stable  
- Compose BOM, Navigation Compose, Hilt, Room, Retrofit/OkHttp, Coil, DataStore  
- Coroutines + Flow  
- Testing: JUnit5/4 + Truth/Kotest (team choice in plan), Turbine for Flow, Room in-memory  
- Static analysis: detekt + ktlint (or spotless)  

## 10. Build & delivery

- `./gradlew :app:assembleDebug` → internal APK  
- `assembleRelease` with **internal** keystore (documented, not committed secrets)  
- Application id TBD: e.g. `com.ygochecker.android`  
- Min SDK: 26+ (configurable in plan); target/compile SDK current  

## 11. Parity / acceptance (v1)

1. Create a deck, add cards via search, persist across process death.  
2. Switch format → copy limits / illegal indicators update.  
3. Export text and YDKE; re-import into a new deck with same passcodes/sections.  
4. Unresolved text names surface user-visible error keys (no crash).  
5. Works on airplane mode for **seeded** catalog search + existing decks; images may be missing offline.  
6. Module dependency graph respects rules in §4 (CI check or `dependencyGuard` optional later).  
7. Unit tests cover YDKE round-trip and text parse/format for representative lists.  

## 12. Roadmap after v1

1. Assist synergies (port scoring concepts from web knowledge index)  
2. Combo explorer  
3. Full offline pack download  
4. Overlay / Flow only if still product-priority  
5. Optional KMP extraction of `core:model` + parsers once stable  

## 13. Risks

| Risk | Mitigation |
|------|------------|
| Catalog size / first install | Seed strategy + incremental sync; measure APK size |
| Drift vs web rules | Shared fixtures (YDKE/text golden files) in both repos |
| Scope creep into Assist | Explicit non-goals; separate feature modules later |
| Banlist freshness | Sync on app start + Settings “Aggiorna banlist” |

## 14. Open points (resolve in implementation plan)

- Exact seed generation pipeline from `ygo-card-checker` assets vs SQLite export  
- Whether `feature:legality` is standalone screen or only embedded chips in editor (lean **embedded + settings format**)  
- Final `applicationId` and versionName scheme  

---

## Spec self-review

- [x] No TBD placeholders for locked decisions  
- [x] v1 scope vs deferred explicit  
- [x] Module SRP and dependency direction stated  
- [x] Acceptance criteria testable  
- [x] No contradiction with hybrid data + future offline  
