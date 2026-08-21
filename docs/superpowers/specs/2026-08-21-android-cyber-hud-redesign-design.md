# YGOChecker Android — Cyber Duel HUD redesign

Branch: `claude/app-improvements-ux-xzo4yw`. Follows the 2026-08-21 "reimagining pass" (commit range 698814c..8711a8e, see PROGRESS.md).

## Goal

Full visual redesign of the Android app: modern look, Yu-Gi-Oh-themed iconography, comfortable navigation. Not a rebuild — the app is mature (real hand-test engine, ATK/DEF search, social backend on Supabase). Scope is a system-wide skin + icon system + two targeted IA improvements, not a ground-up re-architecture. Validated interactively via the brainstorming visual companion (mockups retained under `.superpowers/brainstorm/`, gitignored).

## Design system

**Direction**: "Cyber Duel HUD" — sci-fi Duel Disk terminal, not trading-card foil. Chosen over two alternatives (Holographic Foil, Collector Card) via visual comparison.

**Palette** (replaces `DuelDarkColorScheme` in `core/designsystem/Theme.kt`):
- Background `#121620`, card surface `#1a1f2c`
- Cyan `#00E5FF` — primary / active state
- Purple `#B14EFF` — secondary / Flow feature
- Green `#3DDC97` — success / legal
- Magenta `#E85AA0` — Trap / destructive-adjacent accents
- Gold `#E8C45C` — **restricted** to rarity, DIVINE attribute, and legality-gold moments only; no longer the default system accent

**Surface treatment**: solid fills, not the current `1px` full border. Cards get a 3px left accent bar in the relevant semantic color (mirrors the already-approved Home mockup). No scanlines, no heavy glow — the "HUD Sobrio" variant won over "HUD Marcato" specifically for long-session readability.

**Typography**: unchanged. Rajdhani (`BrandFont`) for headlines already reads as sci-fi/Duel Disk — no swap needed.

**Spacing/shapes**: unchanged. `DuelSpacing` (4/8/12/16dp scale) and `DuelShapes` (4–28dp radii) already fit; reuse as-is.

## Iconography

Two new reusable components in `core/designsystem`, replacing generic Material icons app-wide.

**Card-type badge** — mini card silhouette (not an abstract glyph): rounded-rect frame with a two-zone fill (art-box + text-box), bordered in the type color.
- Normal/Monster: yellow `#E8C45C` frame
- Spell: green `#3DDC97` frame
- Trap: magenta `#E85AA0` frame
- Extra Deck (Fusion/Synchro/Xyz/Link combined): purple `#B14EFF` frame — sub-type distinction stays in the card detail view only, not the badge

**Attribute badge** — glossy sphere + kanji character + small name banner, one per element (WIND 風, WATER 水, EARTH 地, FIRE 炎, DARK 闇, LIGHT 光, DIVINE 神). Original rendering (radial-gradient sphere, custom highlight/shadow), not a copy of any reference image — kanji is plain text via system CJK font fallback, carries no copyright; the sphere+kanji+banner *composition* is a generic badge pattern, not Konami-specific. Each sphere's base color follows the element (fire=red, water=blue, etc.) rather than being forced into the cyan/purple system palette — these are semantic, not brand, colors.

**Direct implementation targets already found in code** (from screen audit):
- `SearchScreen.kt` type filter chips currently use `PersonAdd/AutoFixHigh/Inventory2/AutoAwesome` → swap to card-type badge, no layout change
- `SearchScreen.kt` already has a complete attribute→icon map (`DarkMode/WbSunny/Terrain/WaterDrop/LocalFireDepartment/Air/AutoAwesome` for DARK/LIGHT/EARTH/WATER/FIRE/WIND/DIVINE) → drop-in swap to attribute badge, same map shape
- Card rows in `DecklistScreen.kt` and `ProfileScreen.kt` (public deck browsing) render the same generic icons → same swap
- Mechanic-tag icons (gy_effect, special-summon, negates, etc.) are a separate system — out of scope, untouched

## Navigation

**Current state**: no `NavHost` — manual `section` state + `HorizontalPager`, 4-tab `NavigationBar` (Search, Decks, Flow, Home — Home rightmost), with Profile/Settings/Overlay in a `ModalNavigationDrawer` (deliberate split per existing code comment).

**New state**: promote to **5 tabs** — Home, Search, Decklist, Flow, Profile, in that order (Home first). The drawer is removed — its reason to exist (keeping Profile "lower billing") goes away once Profile is a first-class tab. Settings moves to a gear icon in the top app bar, present on every tab; Overlay's permission screen is reachable from inside Settings (it's a config screen, not a peer destination). Active-tab indicator: cyan pill, matching the validated mockup. Back-stack behavior (the recent `BackHandler` work) is preserved — this is a relabeling/promotion of destinations, not a rewrite of back navigation.

## Per-screen changes

| Screen | Change |
|---|---|
| **Home** | Restyle to new palette/cards. Add a direct Decklist shortcut (currently only reachable via the last-edited-deck card or the tab) — the one genuine gap found in audit. |
| **Search** | Restyle + badge swap (type chips, attribute filter/display) as above. No structural change — current `Scaffold` + filter bottom sheet pattern is sound. |
| **Decklist** | Restyle + badge swap in card rows. Keep the existing list↔editor state-swap pattern (not a nav route) and `PrimaryTabRow` for Main/Extra/Side — it works. |
| **Flow** | Restyle only (colors/cards/badges). Hand-test engine and role analyzer were completed in the prior pass — not touched. |
| **Profile** | Restyle + badge swap only. Largest file (1268 lines), same list↔detail internal-state pattern as Decklist across several sub-flows (search, chat, editor). Highest regression risk — implement incrementally, screen-section by screen-section, not as one sweeping patch. |
| **Settings** | Restyle + add section-header icons (Format/Language/Account/Sync/Update) — currently icon-light. Pure visual add, no IA risk. Gains the Overlay entry point per the nav change above. |
| **Overlay** | Restyle to match Settings (it's a permissions/config screen, not the floating HUD itself — the floating `LegalityOverlayService` HUD's own rendering is untouched). |

## Non-goals

- No change to the hand-test/Flow engine logic, Supabase social backend, or card-legality logic
- No change to mechanic-tag iconography (separate system)
- No pixel-identical reproduction of any third-party attribute-icon artwork (see companion session notes) — original renderings only
- No new nav framework (`NavHost`) — working within the existing manual section/pager approach

## Verification

- `./gradlew testDebugUnitTest` and `:app:assembleDebug` after each screen's changes (already green as of this session — see commit `55ae215`)
- Manual pass on-device/emulator per screen, since this is UI-only work not covered by existing unit tests
- Profile screen: extra manual pass per sub-flow (search, chat, editor) given its size and regression risk noted above
