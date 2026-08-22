# SEGOC Field + Puzzle — Design

**Date:** 2026-08-22  
**Branch:** `refactor/segoc-tmm-field`  
**Status:** Draft for user review  
**Builds on:** `docs/superpowers/specs/2026-08-21-segoc-flow-coach-design.md` (SEGOC profiles + `FindSimultaneousTriggers`, reused)  
**Reference UX:** TrainMyMat field rehearsal; MDPro3 / Master Duel zone language  
**Local MDPro3 root (dev machine):** `C:\Games\MDPro3` (`Picture/Art/{passcode}.jpg`, ~14.8k files)

## Goal

Turn Flow from a badge/warning list into a **step-by-step SEGOC lesson** on the cards actually in the active deck, playable on a **fullscreen MDPro3-layout field**, plus a **Puzzle** mode that instantiates real chain cases against importable opponent decks. Delete the MDPRO overlay (HUD / MediaProjection / OCR): it does not work and is out of product.

This is a rules-education + drill layer, not a duel simulator.

## Locked decisions

| Topic | Choice |
|-------|--------|
| Engine | Scripted FSM (`SegocLesson` + `PuzzleBoard`). No `ocgcore`, no Lua at runtime, no embedding MDPro3. |
| Graphics | Compose field matching MDPro3 / Master Duel **zone layout**. Card (and optional frame/closeup) bitmaps loaded **at runtime** from the user's MDPro3 install. |
| MDPro3 files in git / APK | **Forbidden.** Resolver only. Fallback: existing YGOPRODeck artwork pipeline. |
| Overlay | Delete `feature:overlay` and all navigation into it. |
| Combo language | Generated **SEGOC lessons** (event → who triggers → APNAP → LIFO) from deck composition. Curated HAT `FlowGraph` / ComboAssist remain as optional extra steppers when the deck matches. No auto-invented 15-step openings from Lua. |
| Puzzles | Template instantiation on **real passcodes** from your deck + an opponent deck. Win conditions with a unique correct answer. |
| Opponent decks | Import YDK / YDKE / `qty name` text; tag `puzzle_opponent`. Ship ≥2 seed lists as text assets. |
| Orientation | Puzzle + field rehearsal: landscape fullscreen, no bottom bar. Portrait coach list stays on the Flow tab. |
| User-facing errors | `AppResult` + `errorKey` i18n. No throws for business flow. |

## Architecture

```
MDPro3 root (user)          YGOPRODeck fallback
        \                     /
         \                   /
          MdproAssetResolver
                 |
Segoc profiles + Deck A + Deck B (opp)
                 |
         SegocLessonBuilder
                 |
        SegocLesson (steps)
           /           \
    Flow coach      PuzzleRunner
    (portrait)      (landscape FieldView + win check)
```

Reuse as-is:

- `SegocProfileSummary`, `TriggerEvent`, `findSimultaneousTriggerPairs`
- `GetSegocProfiles`, `FindSimultaneousTriggers`
- `FlowRehearsalEngine` for curated HAT graphs only (unchanged)
- Deck import (`PersistImportedDeck`, text/YDKE)

New (Kotlin, `core:model` / `core:domain` / `feature:flow`):

- `MdproAssetResolver` — `data:cards` (file I/O) behind a domain port
- `SegocLesson`, `SegocStep`, `PuzzleBoard`, `PuzzleInstance`
- `BuildSegocLessons`, `InstantiatePuzzles`
- `FieldView` composable in `feature:flow` (or `feature:puzzle` if FlowScreen grows past ~700 lines; prefer split `feature:puzzle` if FieldView + runner exceed one file's job)

## AssetResolver

Port: `MdproAssetLocator`.

```kotlin
interface MdproAssetLocator {
    /** Null = not configured / file missing. */
    fun cardArt(passcode: Int): Uri?
    fun closeup(passcode: Int): Uri?
    fun overframe(): Uri?
}

data class MdproAssetSettings(
    val rootPath: String?,          // SAF tree URI on Android; filesystem path on debug desktop
    val enabled: Boolean,
)
```

On-disk layout relative to user-picked MDPro3 root (verified against local install):

| Kind | Relative path |
|------|----------------|
| Card art | `Picture/Art/{passcode}.jpg` |
| Closeup | `Picture/Closeup/{passcode}.jpg` (if absent, skip) |
| Frames | `Picture/OverFrame/` (optional; field works without) |

Settings row: “Cartella MDPro3” → Android Storage Access Framework tree picker, persist URI with persistable permission. Empty / denied → fallback art only, field still renders.

Do not scan or copy `MDPro3_Data` (Unity bundles). Do not decrypt Master Duel packs. Loose `Picture/` files only.

## FieldView (MDPro3 layout)

Master Duel / MDPro3 zone language, landscape:

- Opponent: Extra Deck, Deck, GY, Banish, 5 S/T (pendulum = leftmost + rightmost), 2 Extra Monster, 5 Main Monster, LP, turn marker
- You: mirrored
- Centre: chain stack, links numbered CL1…CLn (placement order); resolve tooltip uses LIFO
- Hand row along the bottom (you) / top (opp)

Cards are cropped bitmaps from the resolver. Empty zones are outlined slots (same geometry as MD, not a tiny widget).

Field is **display + tap targets**, not a physics/3D renderer. Animations: card slides zone→zone on step advance (short, no VFX dump from MDPro3 `Video/` / `Sound/` in v1).

## SegocLesson (Flow language)

Replaces the pair-card + single rule paragraph as the primary coach content.

```kotlin
enum class SegocStepKind {
    EVENT,          // "Un mostro viene distrutto"
    YOUR_TRIGGERS,  // cards in YOUR deck that share that event
    YOU_ORDER,      // APNAP: you choose order — they go on chain first
    OPP_ORDER,      // opponent's matching triggers (empty if no opp)
    RESOLVE_LIFO,   // last placed resolves first; name the first-to-resolve card
}

data class SegocStepCard(
    val cardId: Int,
    val spellSpeed: Int?,
    val missedTimingRisk: Boolean,
    val zoneHint: String?,          // e.g. GY, FIELD, HAND — from template or "deck composition"
)

data class SegocStep(
    val kind: SegocStepKind,
    val title: String,              // i18n key + args, not free English
    val bodyKey: String,
    val cards: List<SegocStepCard>,
    val event: TriggerEvent?,
)

data class SegocLesson(
    val id: String,                 // stable: event + sorted card ids
    val event: TriggerEvent,
    val yourCardIds: List<Int>,
    val oppCardIds: List<Int>,
    val steps: List<SegocStep>,
)
```

**Builder** (`BuildSegocLessons(yourIds, oppIds, profiles)`):

1. Group TRIGGER cards by non-`OTHER` `TriggerEvent` (same rule as `findSimultaneousTriggerPairs`).
2. Emit a lesson when `yourIds` has ≥2 cards on that event, **or** ≥1 yours and ≥1 opp.
3. Steps always in the order above; skip `OPP_ORDER` if `oppCardIds` empty.
4. Copy: reuse the locked SEGOC sentence (APNAP placement, LIFO resolve) split across `YOU_ORDER` / `OPP_ORDER` / `RESOLVE_LIFO` — not regenerated per pair.
5. Tactical note: same generic-per-`TriggerEvent` lookup as the 2026-08-21 spec; attach to `RESOLVE_LIFO`.

HAT catalog rehearsal stays behind “Browse curated HAT flows”. ComboAssist lines, when present, render with the **same stepper chrome** (title + card ids + existing node body), not a second UI language.

## Puzzle

A puzzle is a `SegocLesson` plus a frozen board and a **unique** win condition (SEGOC “you choose freely” is a lesson, not a puzzle).

```kotlin
enum class PuzzleWin {
    CHAIN_ORDER,     // user must place listed card ids in exactly one order
    MUST_CL1,        // missed-timing: named when-trigger must be CL1
    APNAP_YOU_FIRST, // user must put all of their triggers before any opp trigger
}

data class PuzzleBoardSlot(val zone: FieldZone, val cardId: Int?)

data class PuzzleInstance(
    val id: String,
    val lesson: SegocLesson,
    val board: List<PuzzleBoardSlot>,
    val win: PuzzleWin,
    val expectedChain: List<Int>,   // passcodes in placement order (CL1 first)
    val opponentDeckId: Long?,
)
```

### Templates (closed set)

| Id | Requires | Board sketch | Win |
|----|----------|--------------|-----|
| `lifo_your_two` | 2 your TRIGGER on event E | One of yours in GY/field as “about to cause E”; both shown as pending | `CHAIN_ORDER`: goal card is **last** in placement (resolves first). `expectedChain` is CL1-first, so `[other, goal]`. |
| `apnap_you_vs_opp` | ≥1 your + ≥1 opp TRIGGER on E | Shared event about to happen | `APNAP_YOU_FIRST`, then one locked follow-up: “chi si risolve per primo?” → opp (LIFO). Not a fourth template. |
| `missed_timing_when` | 1 your `missedTimingRisk` + 1 other your SP1 on E | Hand/field when-trigger live | `MUST_CL1` on the when-card |

Goal card for `lifo_your_two`: the card with **higher passcode** among the pair (deterministic, no curation). Copy explains “questo effetto deve risolversi per primo **in questo drill**”, not “è sempre ottimale”.

Instantiation: walk templates × (your deck, selected opp). Skip if not enough matching profiles. Cap 12 puzzles per pair of decks. Sort: missed-timing first, then APNAP, then LIFO.

### Opponent decks

- New deck flag `isPuzzleOpponent: Boolean` (Room column, default false). Import UI: checkbox “Usa come avversario Puzzle”.
- Seed assets: `android/data/cards/src/main/assets/puzzle-opponents/*.txt` (`qty name`, existing DeckText parser). Load once into Room if no seed opponents exist.
- Seeds (names only; lists filled at implementation from HAT-legal cards already in the DB):
  1. `hat-backrow` — trap/GY trigger density (Artifact / Hand / floodgate style)
  2. `empty-control` — almost no shared triggers (negative control: few/no puzzles)

User can import more anytime.

### Runner

Landscape `FieldView` + chain strip. User taps pending trigger portraits to build placement order, Confirm, or uses “Spiega” to drop back to the lesson stepper without failing.

Pass → next puzzle. Fail → flash the expected chain on the stack + `RESOLVE_LIFO` step, stay on the same puzzle.

## Overlay removal

Delete (do not stub):

- Gradle `:feature:overlay` (module, include, `app` dependency)
- `OverlayRoute` / `onOpenOverlay` in `MainActivity`
- Settings row that opens overlay
- Manifest overlay permission / `LegalityOverlayService` entries
- Overlay strings in `core:designsystem` (en + it)
- `MdproOcrParse` tests die with the module

Angular `/overlay` page is out of this Android ship unless it is a one-line nav leftover; do not expand SPA overlay. If a settings link remains, remove it in the same PR only if it is a single obvious reference.

## UI map

- **Flow tab (portrait):** active deck, list of `SegocLesson` as step cards (not pair-paragraph). CTA “Apri sul field” / “Puzzle vs …” if an opponent is selected.
- **Opponent picker:** decks with `isPuzzleOpponent`, plus “Importa avversario”.
- **Field / Puzzle:** new fullscreen route (back closes). No bottom bar.
- HAT catalog: existing secondary entry.

## Error keys (new)

- `flow.lesson.empty` — no simultaneous triggers in this deck (± opp)
- `puzzle.none` — templates did not instantiate
- `puzzle.wrong_chain` — fail feedback
- `mdpro.assets.unavailable` — resolver miss (non-blocking; fallback art)

## Non-goals

- `ocgcore` / live MDPro3 process / Intent-launch as Puzzle renderer
- Vendoring `Picture/`, `Sound/`, `Video/`, Unity `MDPro3_Data`
- Optimal-play solver
- Auto-generated opening combos from Lua
- Overlay salvage
- Portrait field (unreadable; landscape only)

## Implementation slices (one spec, sequential PRs ok)

1. **Delete overlay** — app boots, no Overlay tab, tests that referenced it gone.
2. **AssetResolver + empty FieldView** — pick MDPro3 folder, show art on a static empty MD layout.
3. **Lesson builder + Flow stepper** — unit tests on builder; coach list uses lessons.
4. **Puzzle instantiate + runner** — template tests + one seed opponent; landscape field.

Each slice is independently testable. Do not start 4 before 3 is green.

## Verification

- `BuildSegocLessons` / `InstantiatePuzzles`: JUnit in `core:model` or `core:domain` (fixtures = fake profiles, no Room). Cases: two your DESTROYED; your+opp same event; OTHER ignored; missedTimingRisk → `MUST_CL1`; empty opp skips `OPP_ORDER`; cap 12.
- Overlay: `:feature:overlay` no longer in `settings.gradle.kts`; `assembleDebug` without that project.
- Field: manual emulator landscape with resolver on / off (fallback art).
- Gate: `harness validate` + Android unit tests for touched modules. Do not claim done without `verifier` subagent.
- Existing SEGOC parser / `FindSimultaneousTriggers` tests stay green.

## Acceptance

1. Overlay is gone from nav, settings, and Gradle.
2. Flow shows step lessons built from the active deck’s real SEGOC profiles.
3. With MDPro3 `Picture/Art` selected, FieldView uses those JPEGs; without it, YGOPRODeck art, no crash.
4. Importing/tagging an opponent deck instantiates at least one puzzle when templates match.
5. A wrong chain shows the expected LIFO/APNAP explanation; a correct chain completes the puzzle.
6. No MDPro3 binary assets in git status.
