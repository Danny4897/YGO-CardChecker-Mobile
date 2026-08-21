# SEGOC Flow Coach — Design

**Branch:** `refactor/hat-flow-trainmymat-boost` (rebase onto `main` first — see Rebase plan)
**Builds on:** `docs/superpowers/specs/2026-08-16-hat-flow-trainmymat-design.md` (ComboAssist/FlowModels/GenerateDeckFlows — reused, not replaced)
**Reference verified against:** real EDOPro-family Lua card scripts in `C:\Games\MDPro3\Data\script_extract\script\` (13,527 files), cross-checked against the project's own local cache at `ygo-card-checker/tools/card-knowledge-db/mdpro-scripts/` (13,523 files — effectively full-catalog coverage, already on disk, no re-extraction needed).

**Correction from an earlier draft of this spec:** the whole-catalog per-card knowledge the app displays (`EffectScriptEntity.tagsCsv`/`rolesCsv`, used by Search/Decklist filters) comes from a **remote** JSON (`YgoProDeckClient.EFFECT_SCRIPTS_URL`, hard-coded to `raw.githubusercontent.com/Gabriele-Vantaggiato/ygo-card-checker/...`) — a **different developer's repository**, not one this project controls. `android/tools/reparse_hat_scripts_from_mdpro.py` (Python) only reprocesses the small HAT-curated subset in `android/data/cards/src/main/assets/offline-pack/scripts-hat.json.gz`, and is a secondary tool kept "in sync manually" with the real, whole-catalog-capable parser: `ygo-card-checker/tools/card-knowledge-db/src/mdpro-lua-parser.ts` (TypeScript — `parseMdproLua`/`mdproToEffectScript`, invoked by `build-effect-scripts.ts`/`import-mdpro-scripts.ts` in the same directory), which already runs the same style of structural Lua regex over the full local script cache. SEGOC extraction is added there, in TypeScript, not in the Python script — and its output ships as a **new, self-hosted bundled asset** (see below), not by touching the external `EFFECT_SCRIPTS_URL` data we don't control.

## Goal

Turn the Flow tab from a curated HAT-only flow browser into a rules coach for the deck the user is actively editing: per-card timing badges (Spell Speed, effect type, Missed Timing risk) plus deck-level warnings when two or more cards can trigger simultaneously, explaining the real SEGOC (Simultaneous Effects Go On Chain) rule as it applies to those specific cards. Scope is the whole catalog, not HAT-only — this is a rules-education layer, not a move-optimizer: it teaches the rule and flags when it applies to the user's cards, it does not claim to compute the "best" chain order for every situation.

## Rebase plan

`refactor/hat-flow-trainmymat-boost` (tip `a8cbdc1`) predates the Cyber Duel HUD redesign now on `main` — `HomeScreen.kt`, the social module, `SettingsScreen.kt`, `DecklistScreen.kt`, and `FlowScreen.kt` itself all diverged on both sides. Rebase onto current `main` before starting new work:

```bash
cd android  # or wherever the hat-flow-trainmymat-boost worktree lives
git fetch origin main
git rebase origin/main
```

Expect real conflicts in `FlowScreen.kt` (redesign touched nothing there, so this should be additive — verify), `SettingsScreen.kt` (redesign added section icons + Overlay row; this branch's changes, if any, must layer on top not revert them), `DecklistScreen.kt` (redesign added `AttributeBadge` to editor rows; this branch added combo-coaching hooks — both must survive), and the deleted-then-recreated `data/social`/`feature/home` modules (redesign's versions win — this branch's stale copies of those files should be dropped, not merged). Run the existing test suite after rebase, before writing any new code, to confirm the reused ComboAssist/FlowModels/GenerateDeckFlows infrastructure still compiles and passes against the current `Card` model.

## What already exists (reused as-is)

- `ComboAssist.kt` — `ComboLineAdvice`/`ComboAction`/`ComboChokeAdvice`, evaluates a curated `FlowGraph` against a decklist. Unchanged.
- `FlowModels.kt` — `FlowGraph{nodes, edges}`, `FlowNode.timingWindow: String?` (free-text label today). Unchanged structurally; new SEGOC data is additive, not a replacement of this model.
- `GenerateDeckFlows.kt`/`ComboAssistUseCases.kt` — derive suggestions from `flows-hat.json`/`card-roles-hat.json`. Unchanged — HAT curated flows stay available as a sub-section (see UI below), they're just no longer the tab's main view.
- `EffectMechanicTags.kt`/`EffectTextProfiler.kt` — existing tag vocabulary derived from effect **text** (not Lua). Unchanged; the new SEGOC tags are a parallel, Lua-derived vocabulary, additive to `Card`/`CardEntity`, not a replacement.
- `ygo-card-checker/tools/card-knowledge-db/src/mdpro-lua-parser.ts` and its `build-effect-scripts.ts`/`import-mdpro-scripts.ts` callers — the real, whole-catalog-capable Lua→JSON pipeline. New SEGOC extraction is added alongside it (see below), not replacing its existing role/timing/step parsing. `android/tools/reparse_hat_scripts_from_mdpro.py` (Python) stays untouched — it's a narrower, HAT-only reprocessing tool, not the right extension point for whole-catalog SEGOC data.

## New data: per-card SEGOC tags

Four new fields, derived from the local Lua script cache (`ygo-card-checker/tools/card-knowledge-db/mdpro-scripts/`, 13,523 files) via the same structural-regex approach `mdpro-lua-parser.ts` already uses for role/timing extraction:

```kotlin
enum class SegocEffectType { ACTIVATE, IGNITION, TRIGGER, QUICK, CONTINUOUS, NONE }

enum class TriggerEvent {
    DESTROYED, TO_GRAVE, REMOVED, LEAVES_FIELD,
    SUMMON_SUCCESS, FLIP_SUMMON_SUCCESS, SPECIAL_SUMMON_SUCCESS,
    DISCARDED, DRAWN, DAMAGE, CONTROL_CHANGED, BATTLE_DESTROYED,
    OTHER,
}

data class SegocProfile(
    val effectType: SegocEffectType,
    val spellSpeed: Int,                    // 1, 2, or 3
    val missedTimingRisk: Boolean,          // heuristic — see caveat below
    val triggerEvents: List<TriggerEvent>,  // one per TRIGGER-type effect block; empty if effectType != TRIGGER
)
```

### Extraction rules (verified against real MDPro3 scripts)

**Effect type** — regex over real `EFFECT_TYPE_*` constants, confirmed present in the script library at scale (4939 files w/ `TRIGGER_O`, 1689 w/ `TRIGGER_F`, 1968 w/ `QUICK_O`/`QUICK_F`, 4051 w/ `IGNITION`):

| Constant (as it appears in Lua) | Hex | Maps to |
|---|---|---|
| `EFFECT_TYPE_ACTIVATE` | `0x0010` | ACTIVATE (normal Spell/Trap activation) |
| `EFFECT_TYPE_IGNITION` | `0x0040` | IGNITION |
| `EFFECT_TYPE_TRIGGER_O` | `0x0080` | TRIGGER (optional, "you can") |
| `EFFECT_TYPE_QUICK_O` | `0x0100` | QUICK (optional) |
| `EFFECT_TYPE_TRIGGER_F` | `0x0200` | TRIGGER (mandatory, "must") |
| `EFFECT_TYPE_QUICK_F` | `0x0400` | QUICK (mandatory) |
| `EFFECT_TYPE_CONTINUOUS` | `0x0800` | CONTINUOUS |

A card can have multiple effect blocks (multiple `local eN=Effect.CreateEffect(c)` ... `c:RegisterEffect(eN)` sequences) with different types — extraction must be **per effect block**, not whole-file, matching the Missed Timing rule below. `SegocProfile.effectType` on the `Card` model takes the "most significant" block for badge purposes (TRIGGER > QUICK > IGNITION > CONTINUOUS > ACTIVATE, i.e. prefer showing the type most relevant to SEGOC); the full per-block list is preserved internally for the multi-trigger detector (below), not discarded.

**Spell Speed** — **not extractable from Lua** (confirmed: zero literal `SPELL_SPEED` occurrences across all 13,527 scripts — it's engine-internal, derived from card type + effect type, never written into card scripts). Derive instead from the `Card.type` string already in the DB, exactly like the existing `isExtraDeckType`/type-classification logic:

```
Counter Trap                          → 3
Quick-Play Spell, Normal Trap,
  Continuous Trap, or QUICK_O/QUICK_F
  present on any monster effect block  → 2
Everything else with any effect block  → 1
No effect block (vanilla)              → not applicable (null)
```

**Missed Timing risk** — real, verified mechanism: `EFFECT_FLAG_DELAY` (`0x10000`) is set via a **separate** `e1:SetProperty(EFFECT_FLAG_DELAY)` call in the same effect block as a `TRIGGER_O`/`TRIGGER_F` type. Its presence means the trigger is an "if" condition (delayed/persistent window — not risking Missed Timing); its absence on a `TRIGGER_O`/`TRIGGER_F` block means a "when" condition (must activate at the moment the event happens, in the window before the next player action — risks Missed Timing if the window passes). Rule: `missedTimingRisk = true` iff the block has `TRIGGER_O` or `TRIGGER_F` AND does not also have `SetProperty(EFFECT_FLAG_DELAY)` in that same block.

**This is a heuristic, not certainty** — real Missed Timing rulings occasionally depend on card-specific text nuances beyond what a flag captures. `SegocProfile.missedTimingRisk` must always be presented in the UI as "generally must activate immediately (verify wording)" phrasing, never as an absolute ruling. Real example fixture from MDPro3 worth using as a regression test: `c11662742.lua` (3 `TRIGGER_F` blocks, no `DELAY` on any — all three should flag `missedTimingRisk = true`).

**Extraction script**: new TypeScript module `ygo-card-checker/tools/card-knowledge-db/src/segoc-parser.ts`, following `mdpro-lua-parser.ts`'s existing structural-regex style (per-effect-block scanning is new — today's `parseMdproLua` regexes whole-file — the SEGOC fields are the first thing in this pipeline that need per-block scoping, since a card can mix "when"/"if" triggers or Trigger+Ignition blocks). A new build script `build-segoc-profiles.ts` (sibling of the existing `build-effect-scripts.ts`) runs it over every file in the local `mdpro-scripts/` cache (13,523 files, already on disk — no re-extraction from `C:\Games\MDPro3` needed for routine rebuilds) and writes one compact whole-catalog JSON, `segoc-profiles.json`, keyed by card passcode.

**Delivery to the app**: gzip `segoc-profiles.json` and copy it into `android/data/cards/src/main/assets/offline-pack/segoc-profiles.json.gz` as a **new bundled asset** — same pattern as `cards-hat.json.gz`/`related-hat.json.gz`, not a remote fetch. At whole-catalog scale (13.5k cards × ~4 small fields) this is expected to land well under 300KB gzipped, in line with the existing bundled packs' sizes (428KB–809KB). Loaded into a new Room table (`segoc_profiles`, `cardId` PK) at the same `ensureBundledKnowledge()` step that loads the other bundled packs today — additive, no change to the existing `EFFECT_SCRIPTS_URL`/HAT-scripts/card-text-enrichment paths for `EffectScriptEntity`.

## New logic: simultaneous-trigger detector

Given the active decklist, find pairs (or larger groups) of cards whose Lua-derived **trigger event** is the same real-world event — this needs a fourth extracted field, `triggerEvents`, distinct from the `EffectMechanicTags` vocabulary already in the codebase (that vocabulary tags what an effect *does* — `destroys`, `banishes`, `searches_deck` — derived from effect *text*; it does not capture what *event* a Trigger effect fires on, which is what SEGOC overlap actually needs).

**`triggerEvent` extraction** — verified against real MDPro3 scripts: trigger-type effect blocks wire to a real-world event via `SetCode(EVENT_X)` (not `SetTrigger`, confirmed from real card scripts, e.g. `c12018201.lua:21` `e2:SetCode(EVENT_DESTROYED)`), scoped to the same effect block as the `EFFECT_TYPE_TRIGGER_O`/`TRIGGER_F` regex match. Relevant `EVENT_*` constants confirmed present at extractable scale (`EVENT_DESTROYED` alone: 633 files) — `EVENT_DESTROYED`, `EVENT_TO_GRAVE`, `EVENT_REMOVE` (banished), `EVENT_LEAVE_FIELD`/`EVENT_LEAVE_FIELD_P`, `EVENT_SUMMON_SUCCESS`/`EVENT_FLIP_SUMMON_SUCCESS`/`EVENT_SPSUMMON_SUCCESS`, `EVENT_DISCARD`, `EVENT_DRAW`, `EVENT_DAMAGE`, `EVENT_CONTROL_CHANGED`, `EVENT_BATTLE_DESTROYED`. Map each to a small closed `TriggerEvent` enum (one entry per constant above); unmapped/rare events fall back to `TriggerEvent.OTHER` and are excluded from pairing (no overlap claimed on an unclassified event).

Two decklist cards are a "simultaneous trigger candidate" pair when both have `SegocProfile.effectType == TRIGGER` and share the same non-`OTHER` `triggerEvent`.

For each candidate pair/group, the coach surfaces:

1. **The rule** (fixed, correct copy, not per-pair generated): *"Se questi si attivano insieme: tra i TUOI trigger scegli tu l'ordine — vanno in chain per primi. Poi l'avversario sceglie l'ordine dei suoi — vanno dopo. La chain risolve a ritroso (LIFO): quindi gli ultimi messi in chain (quelli dell'avversario, se presenti) si risolvono per primi."* (APNAP placement order, LIFO resolution — this is the actual SEGOC rule, verified independently, not sourced from MDPro3's compiled engine which wasn't inspectable).
2. **Which of the user's own cards are in play at once** (both are in the decklist — doesn't mean both are always simultaneously live, this is a deck-composition-level heads-up, not a board-state simulator).
3. A generic tactical note pulled from a small fixed lookup by `TriggerEvent` category (e.g. for `EVENT_DESTROYED`/`EVENT_TO_GRAVE` overlaps: "conviene di solito decidere l'ordine in base a quale effetto ti dà più informazione prima dell'altro") — explicitly generic, reused across all pairs sharing that event category, not hand-authored per card pair. This is the one place scope stays bounded: no per-card-pair curation, no claim of optimality.

## UI: Flow tab becomes the active-deck coach

Reusing `HomeInsets`/`ThemedScreenHeader`/existing card-row patterns from the redesign:

- **Primary view**: the currently-open/last-edited deck's card list, each row showing the existing card art + name (matching `SearchCardRow`/`EditorCardRow` pattern) plus a new compact timing badge (Spell Speed number + effect-type glyph, reusing the `compact` badge sizing convention from `CardTypeBadge`/`AttributeBadge`) and a small warning icon on cards flagged `missedTimingRisk`.
- **Simultaneous-trigger warnings**: a card/section per detected pair/group, showing the two cards, the fixed rule text, and the generic tactical note.
- **HAT catalog + rehearsal**: demoted to a secondary section/tab within Flow (still reachable, unchanged functionality) — not deleted, not the default view.
- **Empty state**: no active deck → same "open or import a deck first" pattern already used elsewhere (Decklist/Profile empty states).

## Non-goals

- No real board-state/duel simulation — this reasons over decklist composition, not a live game state.
- No claim of "optimal" chain order for every specific interaction — the rule + heads-up is the deliverable, not a solved combo line (that remains the curated HAT flow catalog's job, unchanged).
- No new Lua execution at runtime — extraction is a build-time script step (like `has_ss` today), not an in-app Lua interpreter.
- No expansion of the curated `flows-hat.json` catalog itself in this pass — that stays as-is, demoted but functional.
- Spell Speed 1/2/3 is derived, not simulated — no attempt to model chain-Spell-Speed-matching legality (e.g. "can this respond") beyond showing the number.

## Verification

- Unit tests on `segoc-parser.ts` against real fixture scripts (including `c11662742.lua`-style multi-trigger-block cases), matching this project's existing `effect-parser.test.ts` conventions, before touching Kotlin.
- Unit tests on the Kotlin-side `SegocProfile` parsing/derivation logic and the simultaneous-trigger pair detector (`core:model`/`core:domain`, JUnit4, matching this repo's existing test conventions).
- Manual on-device check of the new Flow tab view with a real decklist, and install on an emulator/device before every commit that touches app code — per this project's established workflow rule, not just a final pass (see project feedback memory `feedback-emulator-before-commit`).
