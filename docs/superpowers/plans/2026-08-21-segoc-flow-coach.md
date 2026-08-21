# SEGOC Flow Coach Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract real SEGOC/timing data (Spell Speed, effect type, Missed Timing risk, trigger event) for the whole card catalog from cached EDOPro Lua scripts, ship it as a new bundled Android asset, and turn the Flow tab into a rules coach on the user's active deck instead of a HAT-only curated flow browser.

**Architecture:** Two codebases, one new data file between them. `ygo-card-checker/tools/card-knowledge-db/` (TypeScript, Node) gains a pure `segoc-parser.ts` module wired into the existing whole-catalog `build-effect-scripts.ts` loop, producing `segoc-profiles.json`. That file is gzipped and copied into the Android app's bundled assets, loaded into a new Room table at the same point the app loads its other bundled packs, exposed through a new domain port, and consumed by a new `FindSimultaneousTriggers` detector and a rebuilt Flow tab UI. The existing HAT-curated `ComboAssist`/`FlowModels`/`GenerateDeckFlows` system is untouched and becomes a secondary, still-reachable section.

**Tech Stack:** TypeScript/Node (`node:assert`, `node --import tsx`, `node:sqlite`) for extraction; Kotlin/Jetpack Compose/Room/Hilt for the app.

## Global Constraints

- Design spec: `docs/superpowers/specs/2026-08-21-segoc-flow-coach-design.md` — read it before starting.
- Branch: `refactor/segoc-flow-coach` (already rebased onto current `main`, includes the Cyber Duel HUD redesign + the reintegrated HAT Flow/Combo Assist work).
- Real EDOPro constants (verified against `C:\Games\MDPro3\Data\script_extract\script\constant.lua` and real card scripts — do not deviate from these exact names/values):
  - `EFFECT_TYPE_ACTIVATE=0x0010`, `EFFECT_TYPE_IGNITION=0x0040`, `EFFECT_TYPE_TRIGGER_O=0x0080`, `EFFECT_TYPE_QUICK_O=0x0100`, `EFFECT_TYPE_TRIGGER_F=0x0200`, `EFFECT_TYPE_QUICK_F=0x0400`, `EFFECT_TYPE_CONTINUOUS=0x0800`.
  - `EFFECT_FLAG_DELAY=0x10000` — set via a separate `SetProperty(EFFECT_FLAG_DELAY)` call in the same effect block as a `TRIGGER_O`/`TRIGGER_F` type; its absence on such a block means "when" (Missed Timing risk), its presence means "if" (no risk).
  - Trigger events wire via `SetCode(EVENT_X)` (not `SetTrigger`) in the same effect block, e.g. `e2:SetCode(EVENT_DESTROYED)`.
- Real EDOPro Lua card scripts already cached locally at `ygo-card-checker/tools/card-knowledge-db/mdpro-scripts/` (13,523 files, `c{passcode}.lua`) — do not re-extract from `C:\Games\MDPro3` for routine work, the cache is already populated.
- **Test on the emulator before every commit that touches app code** (project rule — see memory `feedback-emulator-before-commit`). Docs-only or TS-only commits don't need this; any Kotlin/Compose commit does: `adb install -r`, launch, navigate to the affected screen, confirm visually.
- Existing systems this plan must not break: `ComboAssist.kt`/`FlowModels.kt`/`GenerateDeckFlows.kt`/`ComboAssistUseCases.kt` (HAT curated flows — stays functional, becomes secondary), `EffectScriptEntity`/`EffectMechanicTags` (unrelated whole-catalog tag system — untouched), `YgoProDeckClient.EFFECT_SCRIPTS_URL` (external repo we don't control — never modify what it points to or assume we can change its content).

---

## Task 1: SEGOC parser (TypeScript, pure functions)

**Files:**
- Create: `ygo-card-checker/tools/card-knowledge-db/src/segoc-parser.ts`
- Create: `ygo-card-checker/tools/card-knowledge-db/src/segoc-parser.test.ts`
- Modify: `ygo-card-checker/package.json` (add `db:test:segoc` script)

**Interfaces:**
- Produces: `type SegocEffectType = 'activate' | 'ignition' | 'trigger' | 'quick' | 'continuous' | 'none'`, `type TriggerEvent = 'destroyed' | 'to_grave' | 'removed' | 'leaves_field' | 'summon_success' | 'flip_summon_success' | 'special_summon_success' | 'discarded' | 'drawn' | 'damage' | 'control_changed' | 'battle_destroyed' | 'other'`, `interface SegocProfile { effectType: SegocEffectType; spellSpeed: number | null; missedTimingRisk: boolean; triggerEvents: TriggerEvent[] }`, `function parseSegocProfile(lua: string): Omit<SegocProfile, 'spellSpeed'>` (pure, Lua-only), `function deriveSpellSpeed(cardType: string, effectType: SegocEffectType): number | null` (pure, Lua-independent — Spell Speed is never written into card scripts, verified: zero literal `SPELL_SPEED` occurrences across the 13,527-script MDPro3 library, so it must come from the card's `type` string, already available in `build-effect-scripts.ts`'s own `cards` query) — both consumed by Task 2.

- [ ] **Step 1: Write the failing tests**

Create `ygo-card-checker/tools/card-knowledge-db/src/segoc-parser.test.ts`:

```typescript
import assert from 'node:assert/strict';
import { parseSegocProfile, deriveSpellSpeed } from './segoc-parser';

// Trigger effect with a "when" condition (no EFFECT_FLAG_DELAY) — Missed Timing risk.
const whenTrigger = `
local e1=Effect.CreateEffect(c)
e1:SetDescription(aux.Stringid(11662742,0))
e1:SetCategory(CATEGORY_TOLIFE)
e1:SetType(EFFECT_TYPE_TRIGGER_F)
e1:SetCode(EVENT_DESTROYED)
e1:SetRange(LOCATION_MZONE)
e1:SetCondition(c1.condition)
e1:SetOperation(c1.operation)
c:RegisterEffect(e1)
`;
const whenResult = parseSegocProfile(whenTrigger);
assert.equal(whenResult.effectType, 'trigger');
assert.equal(whenResult.missedTimingRisk, true);
assert.deepEqual(whenResult.triggerEvents, ['destroyed']);

// Trigger effect with EFFECT_FLAG_DELAY in the same block — "if" condition, no risk.
const ifTrigger = `
local e1=Effect.CreateEffect(c)
e1:SetType(EFFECT_TYPE_TRIGGER_O)
e1:SetCode(EVENT_TO_GRAVE)
e1:SetProperty(EFFECT_FLAG_DELAY)
e1:SetRange(LOCATION_GRAVE)
c:RegisterEffect(e1)
`;
const ifResult = parseSegocProfile(ifTrigger);
assert.equal(ifResult.effectType, 'trigger');
assert.equal(ifResult.missedTimingRisk, false);
assert.deepEqual(ifResult.triggerEvents, ['to_grave']);

// Ignition effect — not a trigger, no Missed Timing concept applies.
const ignition = `
local e1=Effect.CreateEffect(c)
e1:SetType(EFFECT_TYPE_IGNITION)
e1:SetRange(LOCATION_MZONE)
e1:SetCost(c1.cost)
e1:SetOperation(c1.operation)
c:RegisterEffect(e1)
`;
const ignitionResult = parseSegocProfile(ignition);
assert.equal(ignitionResult.effectType, 'ignition');
assert.equal(ignitionResult.missedTimingRisk, false);
assert.deepEqual(ignitionResult.triggerEvents, []);

// Multiple effect blocks: TRIGGER_F when + TRIGGER_O if in the same card — per-block scoping.
const mixedBlocks = `
local e1=Effect.CreateEffect(c)
e1:SetType(EFFECT_TYPE_TRIGGER_F)
e1:SetCode(EVENT_DESTROYED)
c:RegisterEffect(e1)
local e2=Effect.CreateEffect(c)
e2:SetType(EFFECT_TYPE_TRIGGER_O)
e2:SetCode(EVENT_TO_GRAVE)
e2:SetProperty(EFFECT_FLAG_DELAY)
c:RegisterEffect(e2)
`;
const mixedResult = parseSegocProfile(mixedBlocks);
// effectType picks the most SEGOC-relevant across blocks: TRIGGER present, so 'trigger'.
assert.equal(mixedResult.effectType, 'trigger');
// missedTimingRisk is true if ANY trigger block on the card is a "when" (risk exists on this card).
assert.equal(mixedResult.missedTimingRisk, true);
assert.deepEqual(mixedResult.triggerEvents, ['destroyed', 'to_grave']);

// No effect blocks at all (vanilla monster) — 'none', no risk, no events.
const vanilla = `
function c11111111.initial_effect(c)
end
`;
const vanillaResult = parseSegocProfile(vanilla);
assert.equal(vanillaResult.effectType, 'none');
assert.equal(vanillaResult.missedTimingRisk, false);
assert.deepEqual(vanillaResult.triggerEvents, []);

// Quick effect (monster quick effect via QUICK_O) — 'quick', no Missed Timing concept.
const quickMonster = `
local e1=Effect.CreateEffect(c)
e1:SetType(EFFECT_TYPE_QUICK_O)
e1:SetRange(LOCATION_MZONE)
c:RegisterEffect(e1)
`;
const quickResult = parseSegocProfile(quickMonster);
assert.equal(quickResult.effectType, 'quick');
assert.equal(quickResult.missedTimingRisk, false);

// Spell Speed derivation — never read from Lua (verified: not a real Lua constant), always
// derived from the card's type string + its already-extracted effectType.
assert.equal(deriveSpellSpeed('Counter Trap Card', 'activate'), 3);
assert.equal(deriveSpellSpeed('Quick-Play Spell Card', 'activate'), 2);
assert.equal(deriveSpellSpeed('Normal Trap Card', 'activate'), 2);
assert.equal(deriveSpellSpeed('Continuous Trap Card', 'continuous'), 2);
assert.equal(deriveSpellSpeed('Effect Monster', 'quick'), 2); // monster quick effect (QUICK_O/F)
assert.equal(deriveSpellSpeed('Effect Monster', 'trigger'), 1);
assert.equal(deriveSpellSpeed('Normal Monster', 'none'), null); // vanilla, no effect block
assert.equal(deriveSpellSpeed('Spell Card', 'activate'), 1); // Normal Spell

console.log('segoc-parser.test.ts OK');
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd ygo-card-checker && node --import tsx tools/card-knowledge-db/src/segoc-parser.test.ts`
Expected: fails immediately — `segoc-parser.ts` doesn't exist yet (module not found error).

- [ ] **Step 3: Implement**

Create `ygo-card-checker/tools/card-knowledge-db/src/segoc-parser.ts`:

```typescript
export type SegocEffectType = 'activate' | 'ignition' | 'trigger' | 'quick' | 'continuous' | 'none';

export type TriggerEvent =
  | 'destroyed'
  | 'to_grave'
  | 'removed'
  | 'leaves_field'
  | 'summon_success'
  | 'flip_summon_success'
  | 'special_summon_success'
  | 'discarded'
  | 'drawn'
  | 'damage'
  | 'control_changed'
  | 'battle_destroyed'
  | 'other';

export interface SegocProfile {
  effectType: SegocEffectType;
  spellSpeed: number | null;
  missedTimingRisk: boolean;
  triggerEvents: TriggerEvent[];
}

/** The 3 fields `parseSegocProfile` can determine from Lua alone. `spellSpeed` is separate —
 * see `deriveSpellSpeed` below — because it is never written into card scripts (verified: zero
 * literal `SPELL_SPEED` occurrences across the 13,527-script MDPro3 library). */
export type LuaSegocProfile = Omit<SegocProfile, 'spellSpeed'>;

const EVENT_MAP: Record<string, TriggerEvent> = {
  EVENT_DESTROYED: 'destroyed',
  EVENT_TO_GRAVE: 'to_grave',
  EVENT_REMOVE: 'removed',
  EVENT_LEAVE_FIELD: 'leaves_field',
  EVENT_LEAVE_FIELD_P: 'leaves_field',
  EVENT_SUMMON_SUCCESS: 'summon_success',
  EVENT_FLIP_SUMMON_SUCCESS: 'flip_summon_success',
  EVENT_SPSUMMON_SUCCESS: 'special_summon_success',
  EVENT_DISCARD: 'discarded',
  EVENT_DRAW: 'drawn',
  EVENT_DAMAGE: 'damage',
  EVENT_CONTROL_CHANGED: 'control_changed',
  EVENT_BATTLE_DESTROYED: 'battle_destroyed',
};

/** One `local eN=Effect.CreateEffect(c)` ... `c:RegisterEffect(eN)` block. */
interface EffectBlock {
  text: string;
}

/** Splits a card's Lua source into individual effect blocks for per-block SEGOC scanning. */
function splitEffectBlocks(lua: string): EffectBlock[] {
  const blocks: EffectBlock[] = [];
  const starts: number[] = [];
  const startRe = /local\s+\w+\s*=\s*Effect\.CreateEffect\(/g;
  let m: RegExpExecArray | null;
  while ((m = startRe.exec(lua)) !== null) {
    starts.push(m.index);
  }
  for (let i = 0; i < starts.length; i++) {
    const from = starts[i];
    const to = i + 1 < starts.length ? starts[i + 1] : lua.length;
    blocks.push({ text: lua.slice(from, to) });
  }
  return blocks;
}

function blockEffectType(block: string): SegocEffectType {
  // A block can carry multiple EFFECT_TYPE_* flags added together (e.g. TRIGGER_O+CONTINUOUS);
  // priority order matches what's most relevant to show as the card's single badge.
  if (/EFFECT_TYPE_TRIGGER_[OF]/.test(block)) return 'trigger';
  if (/EFFECT_TYPE_QUICK_[OF]/.test(block)) return 'quick';
  if (/EFFECT_TYPE_IGNITION/.test(block)) return 'ignition';
  if (/EFFECT_TYPE_CONTINUOUS/.test(block)) return 'continuous';
  if (/EFFECT_TYPE_ACTIVATE/.test(block)) return 'activate';
  return 'none';
}

function blockTriggerEvents(block: string): TriggerEvent[] {
  const out: TriggerEvent[] = [];
  const re = /SetCode\(\s*(EVENT_[A-Z_]+)\s*\)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(block)) !== null) {
    out.push(EVENT_MAP[m[1]] ?? 'other');
  }
  return out;
}

function blockIsWhenTrigger(block: string): boolean {
  const isTrigger = /EFFECT_TYPE_TRIGGER_[OF]/.test(block);
  if (!isTrigger) return false;
  return !/SetProperty\([^)]*EFFECT_FLAG_DELAY/.test(block);
}

const TYPE_PRIORITY: SegocEffectType[] = ['trigger', 'quick', 'ignition', 'continuous', 'activate', 'none'];

export function parseSegocProfile(lua: string): LuaSegocProfile {
  const blocks = splitEffectBlocks(lua);
  if (blocks.length === 0) {
    return { effectType: 'none', missedTimingRisk: false, triggerEvents: [] };
  }

  const types = blocks.map((b) => blockEffectType(b.text));
  const effectType = TYPE_PRIORITY.find((t) => types.includes(t)) ?? 'none';

  const missedTimingRisk = blocks.some((b) => blockIsWhenTrigger(b.text));

  const events = new Set<TriggerEvent>();
  for (const b of blocks) {
    if (blockEffectType(b.text) !== 'trigger') continue;
    for (const ev of blockTriggerEvents(b.text)) {
      events.add(ev);
    }
  }

  return {
    effectType,
    missedTimingRisk,
    triggerEvents: [...events],
  };
}

/**
 * Spell Speed is engine-internal, never present in card scripts — derived from the card's
 * `type` string (as already stored in this pipeline's `cards` table) plus its already-parsed
 * `effectType`. Returns `null` for vanilla cards (no effect block at all).
 */
export function deriveSpellSpeed(cardType: string, effectType: SegocEffectType): number | null {
  if (effectType === 'none') return null;
  if (cardType.includes('Counter Trap')) return 3;
  if (cardType.includes('Quick-Play Spell')) return 2;
  if (cardType.includes('Trap')) return 2; // Normal Trap, Continuous Trap
  if (effectType === 'quick') return 2; // monster quick effect (QUICK_O/QUICK_F)
  return 1;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd ygo-card-checker && node --import tsx tools/card-knowledge-db/src/segoc-parser.test.ts`
Expected: prints `segoc-parser.test.ts OK`, no assertion errors, exit code 0.

- [ ] **Step 5: Add the npm script**

In `ygo-card-checker/package.json`, add alongside the existing `"db:test:effects"` line:

```json
    "db:test:segoc": "node --import tsx tools/card-knowledge-db/src/segoc-parser.test.ts",
```

- [ ] **Step 6: Commit**

```bash
cd ygo-card-checker
git add tools/card-knowledge-db/src/segoc-parser.ts tools/card-knowledge-db/src/segoc-parser.test.ts package.json
git commit -m "feat(card-knowledge-db): add SEGOC/timing profile parser"
```

(TypeScript-only change — no emulator check needed for this commit.)

---

## Task 2: Wire SEGOC extraction into the whole-catalog build

**Files:**
- Modify: `ygo-card-checker/tools/card-knowledge-db/src/build-effect-scripts.ts`

**Interfaces:**
- Consumes: `parseSegocProfile(lua: string): LuaSegocProfile` and `deriveSpellSpeed(cardType: string, effectType: SegocEffectType): number | null` from Task 1.
- Produces: `src/assets/data/effect-scripts/segoc-profiles.json` (whole-catalog, keyed by card passcode as string, each entry a full `SegocProfile` including `spellSpeed`) — consumed by Task 3 (manual copy into the Android app).

- [ ] **Step 1: Add the SEGOC pass to the existing per-card loop**

`build-effect-scripts.ts` already opens the local SQLite `cards` table, iterates every row, and loads that card's cached Lua via `loadMdproLua(card.id)` inside its `runInTransaction` loop (current file: `ygo-card-checker/tools/card-knowledge-db/src/build-effect-scripts.ts`). The same loop's `card` row already carries `card.type` (see the existing `CardRow` interface: `{ id, name, type, is_extra_deck }`), which `deriveSpellSpeed` needs. Add the SEGOC pass there instead of a second whole-catalog scan.

Add the import, near the existing ones:

```typescript
import { parseSegocProfile, deriveSpellSpeed, type SegocProfile } from './segoc-parser';
```

Add the output path constant, alongside `SCRIPTS_PATH`/`HAT_PATH`:

```typescript
const SEGOC_PATH = join(SCRIPTS_DIR, 'segoc-profiles.json');
```

Add a collector before the `runInTransaction` call:

```typescript
const segocProfiles: Record<string, SegocProfile> = {};
```

Inside the existing `for (const card of cards)` loop, right after the existing `if (lua) { ... }` block that already computes `parsed`/`mdproScript` (the block starting `if (lua) { mdproHits += 1; ... }`), add:

```typescript
      if (lua) {
        const luaProfile = parseSegocProfile(lua);
        segocProfiles[String(card.id)] = {
          ...luaProfile,
          spellSpeed: deriveSpellSpeed(card.type, luaProfile.effectType),
        };
      }
```

(Insert this as an additional statement inside the existing `if (lua) { ... }` block — do not duplicate the `if (lua)` check, and do not move `loadMdproLua` — it's already called once per card as `const lua = loadMdproLua(card.id);` earlier in the loop body.)

- [ ] **Step 2: Write the SEGOC output file**

Add a small write function near `writeAssets`:

```typescript
function writeSegocProfiles(profiles: Record<string, SegocProfile>): void {
  mkdirSync(SCRIPTS_DIR, { recursive: true });
  writeFileSync(SEGOC_PATH, JSON.stringify(profiles), 'utf8');
}
```

In `main()`, after the existing `writeAssets(scripts, meta?.totalCards ?? cards.length);` call, add:

```typescript
  writeSegocProfiles(segocProfiles);
  console.log(`SEGOC profiles → ${SEGOC_PATH} (${Object.keys(segocProfiles).length} cards)`);
```

Also handle the early-return path (the `if (cards.length === 0) { ... return; }` branch near the top of `main()`) — add `writeSegocProfiles({});` there too, right before its `return;`, so the output file always exists even on an empty DB.

- [ ] **Step 3: Run the build and verify real output**

Run: `cd ygo-card-checker && npm run db:effect-scripts`
Expected: exits 0, prints the existing `Compiled N card scripts...` lines plus the new `SEGOC profiles → .../segoc-profiles.json (N cards)` line. `N` for SEGOC should be close to (not necessarily equal to) the `MDPro lua hits` count already printed, since SEGOC only fires where `lua` was found.

- [ ] **Step 4: Spot-check real output**

Run: `cd ygo-card-checker && node -e "const d = require('./src/assets/data/effect-scripts/segoc-profiles.json'); console.log(d['11662742']); console.log(Object.keys(d).length)"`
Expected: prints a real profile object for card 11662742 (from the fixture used in Task 1's tests — confirm it shows `effectType: 'trigger'`, `missedTimingRisk: true`, and a non-null `spellSpeed`, matching the real card's actual Lua and type, not just the synthetic test fixture), and a total count in the thousands (matching real catalog size).

- [ ] **Step 5: Commit**

```bash
cd ygo-card-checker
git add tools/card-knowledge-db/src/build-effect-scripts.ts
git commit -m "feat(card-knowledge-db): extract SEGOC profiles for the whole catalog"
```

Do not commit the generated `src/assets/data/effect-scripts/segoc-profiles.json` itself in this repo unless the existing `scripts.json`/`hat-2014.json` outputs are already tracked in git — check with `git status src/assets/data/effect-scripts/` first and match whatever the existing convention is (if `scripts.json` is gitignored, `segoc-profiles.json` should be too; if tracked, add it the same way).

---

## Task 3: Bundle segoc-profiles.json into the Android app

**Files:**
- Create: `android/data/cards/src/main/assets/offline-pack/segoc-profiles.json.gz` (binary asset, generated, not hand-written)

- [ ] **Step 1: Generate the gzipped asset**

Run (from the repo root, `C:\Users\adani\Desktop\YGOChecker`):

```bash
gzip -9 -c ygo-card-checker/src/assets/data/effect-scripts/segoc-profiles.json > android/data/cards/src/main/assets/offline-pack/segoc-profiles.json.gz
```

- [ ] **Step 2: Check the size**

Run: `ls -la android/data/cards/src/main/assets/offline-pack/segoc-profiles.json.gz`
Expected: comparable to or smaller than the existing `cards-hat.json.gz` (428KB)/`related-hat.json.gz` (809KB). If it's dramatically larger (multiple MB), stop and report — that would mean the per-card profile encoding needs tightening (e.g. shorter JSON keys) before bundling, not a silent accept.

- [ ] **Step 3: Commit**

```bash
cd android
git add data/cards/src/main/assets/offline-pack/segoc-profiles.json.gz
git commit -m "feat(android): bundle whole-catalog SEGOC profiles asset"
```

(Binary asset only, no app code changed yet — no emulator check needed for this specific commit; Task 6's loader wiring is where this asset first actually gets read, and that commit does need the emulator check.)

---

## Task 4: SegocProfile domain model + Room entity

**Files:**
- Modify: `android/core/model/src/main/kotlin/com/ygochecker/core/model/FlowModels.kt` (add the domain-facing types — same file that already holds the other Flow-adjacent models)
- Modify: `android/data/cards/src/main/kotlin/com/ygochecker/data/cards/CardsData.kt` (add `SegocProfileEntity`, DAO methods, Room version bump)
- Create: `android/core/model/src/test/kotlin/com/ygochecker/core/model/SegocProfileTest.kt`

**Interfaces:**
- Produces: `enum class SegocEffectType`, `enum class TriggerEvent`, `data class SegocProfileSummary(cardId: Int, effectType: SegocEffectType, spellSpeed: Int?, missedTimingRisk: Boolean, triggerEvents: List<TriggerEvent>)` (core:model — consumed by Tasks 5, 7, 8), `data class SegocProfileEntity(...)` + `fun SegocProfileEntity.toSummary(): SegocProfileSummary` (data:cards — consumed by Task 5).

- [ ] **Step 1: Write the failing test**

Create `android/core/model/src/test/kotlin/com/ygochecker/core/model/SegocProfileTest.kt`:

```kotlin
package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SegocProfileTest {
    @Test
    fun `SegocEffectType has exactly the values the TS parser emits`() {
        val names = SegocEffectType.entries.map { it.name }
        assertEquals(
            setOf("ACTIVATE", "IGNITION", "TRIGGER", "QUICK", "CONTINUOUS", "NONE"),
            names.toSet(),
        )
    }

    @Test
    fun `TriggerEvent has exactly the values the TS parser emits`() {
        val names = TriggerEvent.entries.map { it.name }
        assertEquals(
            setOf(
                "DESTROYED", "TO_GRAVE", "REMOVED", "LEAVES_FIELD",
                "SUMMON_SUCCESS", "FLIP_SUMMON_SUCCESS", "SPECIAL_SUMMON_SUCCESS",
                "DISCARDED", "DRAWN", "DAMAGE", "CONTROL_CHANGED", "BATTLE_DESTROYED", "OTHER",
            ),
            names.toSet(),
        )
    }

    @Test
    fun `SegocProfileSummary carries the fields the coach needs`() {
        val summary = SegocProfileSummary(
            cardId = 11662742,
            effectType = SegocEffectType.TRIGGER,
            spellSpeed = 1,
            missedTimingRisk = true,
            triggerEvents = listOf(TriggerEvent.DESTROYED),
        )
        assertEquals(11662742, summary.cardId)
        assertEquals(SegocEffectType.TRIGGER, summary.effectType)
        assertEquals(1, summary.spellSpeed)
        assertEquals(true, summary.missedTimingRisk)
        assertEquals(listOf(TriggerEvent.DESTROYED), summary.triggerEvents)
    }

    @Test
    fun `spellSpeed is nullable for vanilla cards with no effect`() {
        val summary = SegocProfileSummary(
            cardId = 90000000,
            effectType = SegocEffectType.NONE,
            spellSpeed = null,
            missedTimingRisk = false,
            triggerEvents = emptyList(),
        )
        assertEquals(null, summary.spellSpeed)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android && ./gradlew --no-daemon :core:model:testDebugUnitTest --tests "com.ygochecker.core.model.SegocProfileTest"`
Expected: FAIL — `SegocEffectType`/`TriggerEvent`/`SegocProfileSummary` are unresolved (compile error).

- [ ] **Step 3: Add the domain types**

In `android/core/model/src/main/kotlin/com/ygochecker/core/model/FlowModels.kt`, add at the end of the file (after the existing `FormatCardRole` data class):

```kotlin

enum class SegocEffectType { ACTIVATE, IGNITION, TRIGGER, QUICK, CONTINUOUS, NONE }

enum class TriggerEvent {
    DESTROYED, TO_GRAVE, REMOVED, LEAVES_FIELD,
    SUMMON_SUCCESS, FLIP_SUMMON_SUCCESS, SPECIAL_SUMMON_SUCCESS,
    DISCARDED, DRAWN, DAMAGE, CONTROL_CHANGED, BATTLE_DESTROYED,
    OTHER,
}

/** Whole-catalog SEGOC/timing profile for one card, extracted from real EDOPro Lua.
 * [spellSpeed] is null for vanilla cards with no effect block — it is never itself read from
 * Lua (not a real Lua constant), it's derived from card type + [effectType] on the TS side. */
data class SegocProfileSummary(
    val cardId: Int,
    val effectType: SegocEffectType,
    val spellSpeed: Int?,
    val missedTimingRisk: Boolean,
    val triggerEvents: List<TriggerEvent>,
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd android && ./gradlew --no-daemon :core:model:testDebugUnitTest --tests "com.ygochecker.core.model.SegocProfileTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Add the Room entity + DAO methods**

In `android/data/cards/src/main/kotlin/com/ygochecker/data/cards/CardsData.kt`, add a new `@Entity` near `EffectScriptEntity` (find the existing `@Entity(tableName = "effect_scripts") data class EffectScriptEntity(...)` block and add this immediately after it):

```kotlin
@Entity(tableName = "segoc_profiles")
data class SegocProfileEntity(
    @PrimaryKey val cardId: Int,
    val effectType: String,
    val missedTimingRisk: Boolean,
    val spellSpeed: Int? = null,
    /** Comma-separated TriggerEvent names, e.g. "DESTROYED,TO_GRAVE". Empty if none. */
    val triggerEventsCsv: String = "",
)
```

In the `@Dao interface CardDao` block, add these methods immediately after the existing `effectScripts(ids: List<Int>): List<EffectScriptEntity>` query (same file, same interface):

```kotlin

    @Query("DELETE FROM segoc_profiles")
    suspend fun clearSegocProfiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegocProfiles(rows: List<SegocProfileEntity>)

    @Query("SELECT COUNT(*) FROM segoc_profiles")
    suspend fun segocProfileCount(): Int

    @Query("SELECT * FROM segoc_profiles WHERE cardId = :id LIMIT 1")
    suspend fun segocProfile(id: Int): SegocProfileEntity?

    @Query("SELECT * FROM segoc_profiles WHERE cardId IN (:ids)")
    suspend fun segocProfiles(ids: List<Int>): List<SegocProfileEntity>
```

Update the `@Database` annotation (find `entities = [CardEntity::class, FormatLimitEntity::class, EffectScriptEntity::class, CardRelationEntity::class, SyncMetaEntity::class], version = 4, exportSchema = false`):

```kotlin
@Database(
    entities = [
        CardEntity::class,
        FormatLimitEntity::class,
        EffectScriptEntity::class,
        CardRelationEntity::class,
        SyncMetaEntity::class,
        SegocProfileEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
```

Add the entity↔summary mapper near the bottom of the same file, following the exact pattern of the existing `private fun EffectScriptEntity.toSummary()`:

```kotlin
private fun SegocProfileEntity.toSummary() = com.ygochecker.core.model.SegocProfileSummary(
    cardId = cardId,
    effectType = com.ygochecker.core.model.SegocEffectType.valueOf(effectType),
    spellSpeed = spellSpeed,
    missedTimingRisk = missedTimingRisk,
    triggerEvents = triggerEventsCsv.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(com.ygochecker.core.model.TriggerEvent::valueOf),
)
```

(`exportSchema = false` on this database — confirmed from the existing annotation — means no Room migration file is needed for the version bump; Room recreates the DB on version mismatch, which is safe here since this whole database is a re-downloadable/re-bundlable cache, not user-authored data.)

- [ ] **Step 6: Build**

Run: `cd android && ./gradlew --no-daemon :core:model:testDebugUnitTest :data:cards:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add android/core/model/src/main/kotlin/com/ygochecker/core/model/FlowModels.kt android/core/model/src/test/kotlin/com/ygochecker/core/model/SegocProfileTest.kt android/data/cards/src/main/kotlin/com/ygochecker/data/cards/CardsData.kt
git commit -m "feat(android): add SegocProfile domain model and Room entity"
```

(No UI/behavior change yet — table exists but nothing loads into it. Emulator check optional here since there's nothing new to observe; Task 5's commit is where loading becomes observable.)

---

## Task 5: Load the bundled SEGOC asset

**Files:**
- Modify: `android/data/cards/src/main/kotlin/com/ygochecker/data/cards/OfflinePackAssets.kt`
- Modify: `android/data/cards/src/main/kotlin/com/ygochecker/data/cards/OfflinePackRepository.kt`
- Modify: `android/core/domain/src/main/kotlin/com/ygochecker/core/domain/Domain.kt` (add port methods to the `OfflinePackRepository` interface)

**Interfaces:**
- Consumes: `SegocProfileEntity`/`toSummary()` from Task 4.
- Produces: `OfflinePackRepository.segocProfile(cardId: Int): SegocProfileSummary?` and `segocProfiles(ids: Collection<Int>): List<SegocProfileSummary>` (core:domain port methods) — consumed by Task 6.

- [ ] **Step 1: Add the asset constant and parser**

In `OfflinePackAssets.kt`, add the constant alongside the existing ones:

```kotlin
    const val SEGOC_PROFILES = "offline-pack/segoc-profiles.json.gz"
```

Bump `PACK_VERSION` so the app knows to reload (find `const val PACK_VERSION = "hat-offline-v3-scripts-no-lua"`):

```kotlin
    const val PACK_VERSION = "hat-offline-v4-segoc-profiles"
```

Add a parser function, following the exact shape of the existing `parseManualSynergies`. It uses the same `optIntOrNull` private extension already defined at the bottom of this file (used today by `parseCards`) — `spellSpeed` is `number | null` on the TS side, so this must not default to a fake `0`:

```kotlin
    fun parseSegocProfiles(json: String): List<SegocProfileEntity> {
        val root = JSONObject(json)
        val out = ArrayList<SegocProfileEntity>(root.length())
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val cardId = key.toIntOrNull() ?: continue
            val o = root.optJSONObject(key) ?: continue
            val events = o.optJSONArray("triggerEvents")
            val eventsCsv = if (events != null) {
                (0 until events.length()).mapNotNull { events.optString(it).ifBlank { null } }
                    .joinToString(",") { it.uppercase() }
            } else {
                ""
            }
            out += SegocProfileEntity(
                cardId = cardId,
                effectType = o.optString("effectType", "none").uppercase(),
                missedTimingRisk = o.optBoolean("missedTimingRisk", false),
                spellSpeed = o.optIntOrNull("spellSpeed"),
                triggerEventsCsv = eventsCsv,
            )
        }
        return out
    }
```

(The TS side emits lowercase string values like `"trigger"`/`"destroyed"` per Task 1's types — `.uppercase()` here converts them to match the Kotlin enum names `TRIGGER`/`DESTROYED` used by `SegocProfileEntity.toSummary()`'s `valueOf(...)` calls from Task 4.)

- [ ] **Step 2: Add the loader in `OfflinePackRepository.kt`**

Add a private loader function, following the exact shape of the existing `loadManualSynergiesFromAsset`:

```kotlin
    private suspend fun loadSegocProfilesFromAsset() {
        val json = OfflinePackAssets.readAssetText(context, OfflinePackAssets.SEGOC_PROFILES)
        val rows = OfflinePackAssets.parseSegocProfiles(json)
        if (rows.isNotEmpty()) {
            rows.chunked(500).forEach { dao.insertSegocProfiles(it) }
        }
        dao.putMeta(SyncMetaEntity(META_SEGOC, OfflinePackAssets.PACK_VERSION))
    }
```

Add the new meta key constant, alongside the existing `META_*` constants (`const val META_SYNERGIES = "manual_synergies"`):

```kotlin
        const val META_SEGOC = "segoc_profiles"
```

Wire it into `ensureBundledKnowledge()` — find the existing body (shown in full below for exact placement) and add the SEGOC load right after the existing effect-scripts load, before `enrichScriptsFromCardTextInternal()`:

```kotlin
    override suspend fun ensureBundledKnowledge() {
        try {
            if (dao.formatLimitCount() == 0) {
                loadFormatLegalityFromAsset()
            } else {
                reloadLimitCache()
            }
            // Fast path only: HAT catalog + effect scripts (LUA AST). Related/synergies = on-demand.
            val coreReady = dao.meta(META_HAT_CORE) == OfflinePackAssets.PACK_VERSION
            if (!coreReady || dao.count() < 1000) {
                loadHatCatalogFromAsset()
            }
            if (!coreReady || dao.effectScriptCount() < 100) {
                // Quiet: must not leave Settings progress stuck at N/N.
                loadHatScriptsPackFromAsset(reportProgress = false)
            }
            if (dao.meta(META_SEGOC) != OfflinePackAssets.PACK_VERSION || dao.segocProfileCount() < 1000) {
                loadSegocProfilesFromAsset()
            }
            enrichScriptsFromCardTextInternal()
            dao.putMeta(SyncMetaEntity(META_HAT_CORE, OfflinePackAssets.PACK_VERSION))
            dao.putMeta(SyncMetaEntity(META_SCRIPTS, System.currentTimeMillis().toString()))
            bump()
        } catch (t: Throwable) {
            android.util.Log.e("OfflinePack", "ensureBundledKnowledge failed", t)
            runCatching { reloadLimitCache() }
        } finally {
            // Belt-and-suspenders: background ingest must never pin the Settings busy bar.
            progress.value = null
        }
    }
```

(Only the new `if (dao.meta(META_SEGOC) != ...) { loadSegocProfilesFromAsset() }` block is new — everything else in this function body is unchanged from the current file, reproduced here so the insertion point is unambiguous.)

- [ ] **Step 3: Add port methods**

In `OfflinePackAssets.kt`'s import section, no change needed (already imports `org.json.JSONObject`).

Add to the `OfflinePackRepository` interface in `Domain.kt` (find `suspend fun effectScripts(ids: Collection<Int>): List<EffectScriptSummary>` inside `interface OfflinePackRepository { ... }` and add immediately after it, before the closing `}`):

```kotlin
    suspend fun segocProfile(cardId: Int): SegocProfileSummary?
    suspend fun segocProfiles(ids: Collection<Int>): List<SegocProfileSummary>
```

Add the import at the top of `Domain.kt` alongside the other `com.ygochecker.core.model.*` imports:

```kotlin
import com.ygochecker.core.model.SegocProfileSummary
```

Implement both in `RoomOfflinePackRepository` (`OfflinePackRepository.kt`), following the exact pattern of the existing `effectScript`/`effectScripts` implementations (add right after them):

```kotlin
    override suspend fun segocProfile(cardId: Int): SegocProfileSummary? =
        dao.segocProfile(cardId)?.toSummary()

    override suspend fun segocProfiles(ids: Collection<Int>): List<SegocProfileSummary> {
        val unique = ids.filter { it > 0 }.distinct()
        if (unique.isEmpty()) return emptyList()
        return dao.segocProfiles(unique).map(SegocProfileEntity::toSummary)
    }
```

- [ ] **Step 4: Build**

Run: `cd android && ./gradlew --no-daemon :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (This will fail to compile if `OfflinePackRepository`'s interface and `RoomOfflinePackRepository`'s implementation are out of sync — that's the compiler catching a real integration error, not a false positive; fix rather than work around.)

- [ ] **Step 5: Emulator check**

Install the built debug APK on the `pixel_7_-_api_36_0` emulator, launch, open Settings → confirm the sync/download flow still completes without error (the new SEGOC load runs inside the same `ensureBundledKnowledge()` path every existing bundled-pack load already goes through — this is the step most likely to surface a crash if the asset JSON shape doesn't match the parser, since it's real device I/O against a real ~13k-entry file, not something the unit build can catch). Check logcat for `OfflinePack` tag errors: `adb logcat -d | grep OfflinePack`.

- [ ] **Step 6: Commit**

```bash
git add android/data/cards/src/main/kotlin/com/ygochecker/data/cards/OfflinePackAssets.kt android/data/cards/src/main/kotlin/com/ygochecker/data/cards/OfflinePackRepository.kt android/core/domain/src/main/kotlin/com/ygochecker/core/domain/Domain.kt
git commit -m "feat(android): load bundled SEGOC profiles into Room"
```

---

## Task 6: Domain port + DI binding

**Files:**
- Modify: `android/core/domain/src/main/kotlin/com/ygochecker/core/domain/Domain.kt`
- Modify: `android/app/src/main/kotlin/com/ygochecker/android/DependencyModule.kt`

**Interfaces:**
- Produces: `fun interface GetSegocProfile { suspend fun invoke(cardId: Int): SegocProfileSummary? }`, `fun interface GetSegocProfiles { suspend fun invoke(ids: Collection<Int>): List<SegocProfileSummary> }` — consumed by Task 8 (Flow UI).

- [ ] **Step 1: Add the ports**

In `Domain.kt`, add right after the existing `GetEffectScripts`/`DefaultGetEffectScripts` block (same file, same pattern):

```kotlin
fun interface GetSegocProfile {
    suspend fun invoke(cardId: Int): SegocProfileSummary?
}
class DefaultGetSegocProfile @Inject constructor(private val pack: OfflinePackRepository) : GetSegocProfile {
    override suspend fun invoke(cardId: Int) = pack.segocProfile(cardId)
}

fun interface GetSegocProfiles {
    suspend fun invoke(ids: Collection<Int>): List<SegocProfileSummary>
}
class DefaultGetSegocProfiles @Inject constructor(private val pack: OfflinePackRepository) : GetSegocProfiles {
    override suspend fun invoke(ids: Collection<Int>) = pack.segocProfiles(ids)
}
```

- [ ] **Step 2: Add DI bindings**

In `DependencyModule.kt`, add two lines matching the exact style of the existing `@Binds abstract fun getEffectScript(value: DefaultGetEffectScript): GetEffectScript` line (same file, same abstract class body):

```kotlin
    @Binds abstract fun getSegocProfile(value: DefaultGetSegocProfile): GetSegocProfile
    @Binds abstract fun getSegocProfiles(value: DefaultGetSegocProfiles): GetSegocProfiles
```

- [ ] **Step 3: Build**

Run: `cd android && ./gradlew --no-daemon :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Hilt will fail the build at compile time if a binding is missing or mismatched — that's the real verification here, no separate unit test needed for pure DI wiring.)

- [ ] **Step 4: Commit**

```bash
git add android/core/domain/src/main/kotlin/com/ygochecker/core/domain/Domain.kt android/app/src/main/kotlin/com/ygochecker/android/DependencyModule.kt
git commit -m "feat(android): add GetSegocProfile/GetSegocProfiles domain ports"
```

(Pure wiring, nothing observable yet — emulator check folded into Task 8 where the ports get a real caller.)

---

## Task 7: Simultaneous-trigger detector

**Files:**
- Create: `android/core/model/src/main/kotlin/com/ygochecker/core/model/SegocDetector.kt`
- Create: `android/core/model/src/test/kotlin/com/ygochecker/core/model/SegocDetectorTest.kt`
- Create: `android/core/domain/src/main/kotlin/com/ygochecker/core/domain/FindSimultaneousTriggers.kt`
- Create: `android/core/domain/src/test/kotlin/com/ygochecker/core/domain/FindSimultaneousTriggersTest.kt`
- Modify: `android/app/src/main/kotlin/com/ygochecker/android/DependencyModule.kt`

**Interfaces:**
- Consumes: `SegocProfileSummary`/`TriggerEvent` from Task 4, `GetSegocProfiles` from Task 6, `DeckRepository.observeDeck(deckId)` (existing port, same access pattern `ComboAssistUseCases.kt` already uses).
- Produces: `data class SimultaneousTriggerPair(cardAId: Int, cardBId: Int, sharedEvent: TriggerEvent)`, `fun findSimultaneousTriggerPairs(cardProfiles: Map<Int, SegocProfileSummary>): List<SimultaneousTriggerPair>` (pure, core:model — testable without DI), `fun interface FindSimultaneousTriggers { suspend fun invoke(deckId: Long): List<SimultaneousTriggerPair> }` (core:domain port — consumed by Task 8).

- [ ] **Step 1: Write the failing pure-function test**

Create `android/core/model/src/test/kotlin/com/ygochecker/core/model/SegocDetectorTest.kt`:

```kotlin
package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegocDetectorTest {
    private fun trigger(cardId: Int, event: TriggerEvent) = SegocProfileSummary(
        cardId = cardId,
        effectType = SegocEffectType.TRIGGER,
        spellSpeed = 1,
        missedTimingRisk = true,
        triggerEvents = listOf(event),
    )

    @Test
    fun `two trigger cards sharing an event form a pair`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            2 to trigger(2, TriggerEvent.DESTROYED),
        )
        val pairs = findSimultaneousTriggerPairs(profiles)
        assertEquals(1, pairs.size)
        assertEquals(TriggerEvent.DESTROYED, pairs[0].sharedEvent)
        assertEquals(setOf(1, 2), setOf(pairs[0].cardAId, pairs[0].cardBId))
    }

    @Test
    fun `cards with different events do not pair`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            2 to trigger(2, TriggerEvent.TO_GRAVE),
        )
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }

    @Test
    fun `non-trigger effect types are excluded even with a shared event`() {
        val ignition = SegocProfileSummary(
            cardId = 3,
            effectType = SegocEffectType.IGNITION,
            spellSpeed = 1,
            missedTimingRisk = false,
            triggerEvents = listOf(TriggerEvent.DESTROYED),
        )
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            3 to ignition,
        )
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }

    @Test
    fun `OTHER event never pairs, even with itself`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.OTHER),
            2 to trigger(2, TriggerEvent.OTHER),
        )
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }

    @Test
    fun `three cards sharing an event produce three pairs`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.TO_GRAVE),
            2 to trigger(2, TriggerEvent.TO_GRAVE),
            3 to trigger(3, TriggerEvent.TO_GRAVE),
        )
        assertEquals(3, findSimultaneousTriggerPairs(profiles).size)
    }

    @Test
    fun `a card is never paired with itself`() {
        val profiles = mapOf(1 to trigger(1, TriggerEvent.DESTROYED))
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android && ./gradlew --no-daemon :core:model:testDebugUnitTest --tests "com.ygochecker.core.model.SegocDetectorTest"`
Expected: FAIL — `findSimultaneousTriggerPairs`/`SimultaneousTriggerPair` unresolved.

- [ ] **Step 3: Implement the pure detector**

Create `android/core/model/src/main/kotlin/com/ygochecker/core/model/SegocDetector.kt`:

```kotlin
package com.ygochecker.core.model

data class SimultaneousTriggerPair(
    val cardAId: Int,
    val cardBId: Int,
    val sharedEvent: TriggerEvent,
)

/**
 * Pure decklist-composition heads-up: which pairs of cards share a real trigger event
 * (per [SegocProfileSummary.triggerEvents]) and could therefore go on the chain together
 * under SEGOC. Does not simulate board state — a deck-composition signal, not a board fact.
 */
fun findSimultaneousTriggerPairs(cardProfiles: Map<Int, SegocProfileSummary>): List<SimultaneousTriggerPair> {
    val triggerCards = cardProfiles.values.filter { it.effectType == SegocEffectType.TRIGGER }
    val byEvent = HashMap<TriggerEvent, MutableList<Int>>()
    for (profile in triggerCards) {
        for (event in profile.triggerEvents) {
            if (event == TriggerEvent.OTHER) continue
            byEvent.getOrPut(event) { mutableListOf() }.add(profile.cardId)
        }
    }
    val out = mutableListOf<SimultaneousTriggerPair>()
    for ((event, cardIds) in byEvent) {
        val distinctIds = cardIds.distinct()
        for (i in distinctIds.indices) {
            for (j in i + 1 until distinctIds.size) {
                out += SimultaneousTriggerPair(distinctIds[i], distinctIds[j], event)
            }
        }
    }
    return out
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd android && ./gradlew --no-daemon :core:model:testDebugUnitTest --tests "com.ygochecker.core.model.SegocDetectorTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Write the failing domain-port test**

Create `android/core/domain/src/test/kotlin/com/ygochecker/core/domain/FindSimultaneousTriggersTest.kt`. This needs a minimal fake `DeckRepository` and `GetSegocProfiles` — check `android/core/domain/src/test/kotlin/com/ygochecker/core/domain/SynergyCompleteDeckTest.kt` or `ComboAssistUseCasesTest.kt` first for this repo's existing fake/mock convention for `DeckRepository` before writing new fakes from scratch, and match it. If those tests use a hand-rolled fake class (not Mockito), write a similarly minimal fake here:

```kotlin
package com.ygochecker.core.domain

import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.SegocEffectType
import com.ygochecker.core.model.SegocProfileSummary
import com.ygochecker.core.model.TriggerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FindSimultaneousTriggersTest {
    @Test
    fun `returns pairs for the deck's own trigger cards`() = runBlocking {
        // NOTE: adapt this fake to whatever DeckRepository fake convention
        // SynergyCompleteDeckTest.kt / ComboAssistUseCasesTest.kt already use in this module —
        // do not introduce a second, inconsistent fake style.
        val deck = fakeDecklistWithCards(cardIds = listOf(1, 2))
        val decks = object : DeckRepository {
            override fun observeDeck(id: Long) = MutableStateFlow(deck)
            // ... other DeckRepository members delegate to TODO()/error("not used") per this
            // module's existing fake convention — see the reference test files above.
        }
        val profiles = object : GetSegocProfiles {
            override suspend fun invoke(ids: Collection<Int>) = listOf(
                SegocProfileSummary(1, SegocEffectType.TRIGGER, 1, true, listOf(TriggerEvent.DESTROYED)),
                SegocProfileSummary(2, SegocEffectType.TRIGGER, 1, true, listOf(TriggerEvent.DESTROYED)),
            )
        }
        val useCase = DefaultFindSimultaneousTriggers(decks, profiles)
        val result = useCase.invoke(deckId = 1L)
        assertEquals(1, result.size)
        assertEquals(TriggerEvent.DESTROYED, result[0].sharedEvent)
    }
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `cd android && ./gradlew --no-daemon :core:domain:testDebugUnitTest --tests "com.ygochecker.core.domain.FindSimultaneousTriggersTest"`
Expected: FAIL — `FindSimultaneousTriggers`/`DefaultFindSimultaneousTriggers` unresolved, and/or the fake `DeckRepository` doesn't compile until you've matched it to the module's real fake pattern from Step 5's note. Resolve the fake shape first (read the referenced existing test files), then re-run this step — don't guess at `DeckRepository`'s full member list blind; it's a real existing interface, read it (`grep -n "interface DeckRepository" -A 15 android/core/domain/src/main/kotlin/com/ygochecker/core/domain/Domain.kt`) before writing the fake.

- [ ] **Step 7: Implement**

Create `android/core/domain/src/main/kotlin/com/ygochecker/core/domain/FindSimultaneousTriggers.kt`:

```kotlin
package com.ygochecker.core.domain

import com.ygochecker.core.model.SegocProfileSummary
import com.ygochecker.core.model.SimultaneousTriggerPair
import com.ygochecker.core.model.findSimultaneousTriggerPairs
import kotlinx.coroutines.flow.first
import javax.inject.Inject

fun interface FindSimultaneousTriggers {
    suspend fun invoke(deckId: Long): List<SimultaneousTriggerPair>
}

class DefaultFindSimultaneousTriggers @Inject constructor(
    private val decks: DeckRepository,
    private val segocProfiles: GetSegocProfiles,
) : FindSimultaneousTriggers {
    override suspend fun invoke(deckId: Long): List<SimultaneousTriggerPair> {
        val deck = decks.observeDeck(deckId).first() ?: return emptyList()
        val cardIds = deck.cards.map { it.card.id }.distinct()
        if (cardIds.isEmpty()) return emptyList()
        val profiles: Map<Int, SegocProfileSummary> = segocProfiles.invoke(cardIds).associateBy { it.cardId }
        return findSimultaneousTriggerPairs(profiles)
    }
}
```

- [ ] **Step 8: Run both tests to verify they pass**

Run: `cd android && ./gradlew --no-daemon :core:model:testDebugUnitTest :core:domain:testDebugUnitTest --tests "com.ygochecker.core.model.SegocDetectorTest" --tests "com.ygochecker.core.domain.FindSimultaneousTriggersTest"`
Expected: PASS (6 + 1 tests).

- [ ] **Step 9: Add the DI binding**

In `DependencyModule.kt`, add alongside the Task 6 bindings:

```kotlin
    @Binds abstract fun findSimultaneousTriggers(value: DefaultFindSimultaneousTriggers): FindSimultaneousTriggers
```

- [ ] **Step 10: Build**

Run: `cd android && ./gradlew --no-daemon :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add android/core/model/src/main/kotlin/com/ygochecker/core/model/SegocDetector.kt android/core/model/src/test/kotlin/com/ygochecker/core/model/SegocDetectorTest.kt android/core/domain/src/main/kotlin/com/ygochecker/core/domain/FindSimultaneousTriggers.kt android/core/domain/src/test/kotlin/com/ygochecker/core/domain/FindSimultaneousTriggersTest.kt android/app/src/main/kotlin/com/ygochecker/android/DependencyModule.kt
git commit -m "feat(android): add simultaneous-trigger pair detector"
```

(Pure logic + DI wiring, no UI yet — Task 8 is where this becomes visible; emulator check happens there.)

---

## Task 8: Flow tab becomes the active-deck coach

**Files:**
- Modify: `android/feature/flow/src/main/kotlin/com/ygochecker/feature/flow/FlowScreen.kt`
- Modify: `android/core/designsystem/src/main/res/values/strings.xml` and `values-it/strings.xml` (new strings)

**Interfaces:**
- Consumes: `GetSegocProfiles` (Task 6), `FindSimultaneousTriggers` (Task 7), existing `ListDecklists` (already injected in `FlowViewModel`).

- [ ] **Step 1: Extend `FlowViewModel`**

In `FlowScreen.kt`, add the two new constructor dependencies to `FlowViewModel` (find the existing constructor):

```kotlin
@HiltViewModel
class FlowViewModel @Inject constructor(
    formatPreference: FormatPreference,
    listDecks: ListDecklists,
    private val listFlows: ListFlows,
    private val getFlow: GetFlow,
    private val flowLinks: DeckFlowLinkRepository,
    private val getSegocProfiles: GetSegocProfiles,
    private val findSimultaneousTriggers: FindSimultaneousTriggers,
) : ViewModel() {
```

Add the imports at the top of the file, alongside the existing `com.ygochecker.core.domain.*`/`com.ygochecker.core.model.*` imports:

```kotlin
import com.ygochecker.core.domain.FindSimultaneousTriggers
import com.ygochecker.core.domain.GetSegocProfiles
import com.ygochecker.core.model.SegocProfileSummary
import com.ygochecker.core.model.SimultaneousTriggerPair
```

Add new state + a load function, right after the existing `kindFilter` property block (before `fun refresh(...)`):

```kotlin
    var activeDeckId by mutableStateOf<Long?>(null)
        private set
    var segocByCardId by mutableStateOf<Map<Int, SegocProfileSummary>>(emptyMap())
        private set
    var simultaneousPairs by mutableStateOf<List<SimultaneousTriggerPair>>(emptyList())
        private set
    var showCatalog by mutableStateOf(false)
        private set
    var coachLoading by mutableStateOf(false)
        private set

    fun toggleCatalog() {
        showCatalog = !showCatalog
    }

    fun loadCoachForActiveDeck() {
        val deckId = decks.value.maxByOrNull { it.updatedAt }?.id
        activeDeckId = deckId
        if (deckId == null) {
            segocByCardId = emptyMap()
            simultaneousPairs = emptyList()
            return
        }
        viewModelScope.launch {
            coachLoading = true
            val deck = decks.value.first { it.id == deckId }
            val cardIds = deck.cards.map { it.card.id }.distinct()
            segocByCardId = getSegocProfiles.invoke(cardIds).associateBy { it.cardId }
            simultaneousPairs = findSimultaneousTriggers.invoke(deckId)
            coachLoading = false
        }
    }
```

- [ ] **Step 2: Trigger the coach load from `FlowRoute`**

Replace the existing `FlowRoute` function body:

```kotlin
@Composable
fun FlowRoute(vm: FlowViewModel = hiltViewModel()) {
    val format by vm.format.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    LaunchedEffect(format, decks) { vm.refresh(format) }

    val graph = vm.activeGraph
    val eng = vm.engine
    if (graph != null && eng != null) {
        FlowRehearsalScreen(
            graph = graph,
            engine = eng,
            timingError = vm.timingError,
            onBack = vm::closeFlow,
            onRestart = vm::restart,
            onAdvanceDefault = { vm.advanceDefault(1) },
            onAdvanceFailTiming = { vm.advanceDefault(2) },
            onChoose = { edge -> vm.choose(edge, 1) },
        )
    } else {
        FlowCatalogScreen(
            format = format,
            loading = vm.loading,
            summaries = vm.filteredSummaries(),
            linkedIds = vm.linkedIds,
            decks = decks,
            focusDeckId = vm.focusDeckId ?: decks.firstOrNull()?.id,
            kindFilter = vm.kindFilter,
            onKindFilter = vm::toggleKindFilter,
            onFocusDeck = vm::focusDeck,
            onOpen = vm::openFlow,
        )
    }
}
```

with:

```kotlin
@Composable
fun FlowRoute(vm: FlowViewModel = hiltViewModel()) {
    val format by vm.format.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    LaunchedEffect(format, decks) { vm.refresh(format) }
    LaunchedEffect(decks) { vm.loadCoachForActiveDeck() }

    val graph = vm.activeGraph
    val eng = vm.engine
    when {
        graph != null && eng != null -> FlowRehearsalScreen(
            graph = graph,
            engine = eng,
            timingError = vm.timingError,
            onBack = vm::closeFlow,
            onRestart = vm::restart,
            onAdvanceDefault = { vm.advanceDefault(1) },
            onAdvanceFailTiming = { vm.advanceDefault(2) },
            onChoose = { edge -> vm.choose(edge, 1) },
        )
        vm.showCatalog -> FlowCatalogScreen(
            format = format,
            loading = vm.loading,
            summaries = vm.filteredSummaries(),
            linkedIds = vm.linkedIds,
            decks = decks,
            focusDeckId = vm.focusDeckId ?: decks.firstOrNull()?.id,
            kindFilter = vm.kindFilter,
            onKindFilter = vm::toggleKindFilter,
            onFocusDeck = vm::focusDeck,
            onOpen = vm::openFlow,
            onBackToCoach = vm::toggleCatalog,
        )
        else -> {
            val activeDeck = decks.firstOrNull { it.id == vm.activeDeckId }
            FlowCoachScreen(
                deck = activeDeck,
                loading = vm.coachLoading,
                segocByCardId = vm.segocByCardId,
                simultaneousPairs = vm.simultaneousPairs,
                onOpenCatalog = vm::toggleCatalog,
            )
        }
    }
}
```

- [ ] **Step 3: Add `onBackToCoach` to `FlowCatalogScreen` and a back action in its header**

`FlowCatalogScreen`'s current signature ends with `onOpen: (String) -> Unit,`. Add one more parameter:

```kotlin
    onOpen: (String) -> Unit,
    onBackToCoach: () -> Unit,
) {
```

Find its `ThemedScreenHeader(title = ..., subtitle = ...)` call inside the function body and give it a back action — `ThemedScreenHeader` already supports leading-icon slots per the Cyber HUD redesign session's `LocalGoBack`/gear pattern, but this in-tab toggle is simpler: don't wire through the global composition locals (those are for the app-shell-level Settings/Overlay navigation, not for a within-tab local toggle). Instead add a small back row above the header call:

```kotlin
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space2),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackToCoach) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(stringResource(DesignR.string.flow_back_to_coach), style = MaterialTheme.typography.labelLarge)
        }
```

Place this `Row` immediately before the existing `ThemedScreenHeader(...)` call inside `FlowCatalogScreen`.

- [ ] **Step 4: Write `FlowCoachScreen`**

Add this new composable after `FlowRoute` (before the existing `FlowCatalogScreen`):

```kotlin
@Composable
private fun FlowCoachScreen(
    deck: com.ygochecker.core.model.Decklist?,
    loading: Boolean,
    segocByCardId: Map<Int, com.ygochecker.core.model.SegocProfileSummary>,
    simultaneousPairs: List<SimultaneousTriggerPair>,
    onOpenCatalog: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ThemedScreenHeader(
            title = stringResource(DesignR.string.flow_coach_title),
            subtitle = stringResource(DesignR.string.flow_coach_subtitle),
        )
        when {
            deck == null -> EmptyState(
                icon = Icons.Default.AccountTree,
                title = stringResource(DesignR.string.flow_coach_empty_title),
                body = stringResource(DesignR.string.flow_coach_empty_body),
                modifier = Modifier.weight(1f).padding(DuelSpacing.space4),
            )
            loading -> Text(
                text = stringResource(DesignR.string.flow_loading),
                modifier = Modifier.padding(DuelSpacing.space4),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(DuelSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                modifier = Modifier.weight(1f),
            ) {
                if (simultaneousPairs.isNotEmpty()) {
                    item(key = "segoc-header") {
                        Text(
                            text = stringResource(DesignR.string.flow_segoc_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(simultaneousPairs, key = { "${it.cardAId}-${it.cardBId}-${it.sharedEvent}" }) { pair ->
                        SegocWarningCard(pair, deck)
                    }
                }
                item(key = "cards-header") {
                    Text(
                        text = stringResource(DesignR.string.flow_coach_cards_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(deck.cards, key = { it.card.id }) { deckCard ->
                    FlowCoachCardRow(deckCard.card, segocByCardId[deckCard.card.id])
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(DuelSpacing.space4)) {
            OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(DesignR.string.flow_open_catalog))
            }
        }
    }
}

@Composable
private fun SegocWarningCard(pair: SimultaneousTriggerPair, deck: com.ygochecker.core.model.Decklist) {
    val nameA = deck.cards.firstOrNull { it.card.id == pair.cardAId }?.card?.name ?: pair.cardAId.toString()
    val nameB = deck.cards.firstOrNull { it.card.id == pair.cardBId }?.card?.name ?: pair.cardBId.toString()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(DuelSpacing.space4), verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2)) {
            Text("$nameA + $nameB", style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(DesignR.string.flow_segoc_rule_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = segocTacticalNote(pair.sharedEvent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun segocTacticalNote(event: com.ygochecker.core.model.TriggerEvent): String =
    stringResource(DesignR.string.flow_segoc_generic_note)

@Composable
private fun FlowCoachCardRow(
    card: com.ygochecker.core.model.Card,
    segoc: com.ygochecker.core.model.SegocProfileSummary?,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(DuelSpacing.space3),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            Text(card.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (segoc != null) {
                val speedLabel = segoc.spellSpeed?.let { "SP$it " }.orEmpty()
                StatusChip(
                    label = "$speedLabel${segoc.effectType.name}",
                    tone = if (segoc.missedTimingRisk) StatusTone.Warning else StatusTone.Neutral,
                )
            }
        }
    }
}
```

(This deliberately reuses `StatusChip`/`StatusTone` — an existing shared component — for the timing badge rather than introducing a new badge component in this task; a dedicated visual badge matching `CardTypeBadge`/`AttributeBadge`'s glossy-sphere styling from the Cyber HUD redesign is a reasonable follow-up, not required for this feature to work correctly. `segocTacticalNote` currently returns one fixed generic string — the design spec's "small fixed lookup by `TriggerEvent` category" is intentionally simplified to a single string here to keep this task's scope bounded; expanding it to a real per-category lookup table is a direct, low-risk follow-up once the base mechanism ships.)

- [ ] **Step 5: Add the new string resources**

In `android/core/designsystem/src/main/res/values/strings.xml`, add (matching the existing `<string name="flow_*">` block's location — search for `flow_title` and add nearby):

```xml
    <string name="flow_coach_title">Flow</string>
    <string name="flow_coach_subtitle">Timing coach for your active deck</string>
    <string name="flow_coach_empty_title">No active deck</string>
    <string name="flow_coach_empty_body">Open or edit a deck to see its timing coach here.</string>
    <string name="flow_coach_cards_section_title">Cards in this deck</string>
    <string name="flow_segoc_section_title">Simultaneous triggers</string>
    <string name="flow_segoc_rule_text">If these trigger together: order YOUR triggers first (you choose the order) — they go on the chain first. Then your opponent orders theirs — those go on after. The chain resolves last-in-first-out, so whichever went on last resolves first.</string>
    <string name="flow_segoc_generic_note">Usually worth resolving the effect that gives you more information or choice last, so you decide with more data.</string>
    <string name="flow_open_catalog">Browse curated HAT flows</string>
    <string name="flow_back_to_coach">Back to coach</string>
```

In `android/core/designsystem/src/main/res/values-it/strings.xml`, add the Italian equivalents at the same location:

```xml
    <string name="flow_coach_title">Flow</string>
    <string name="flow_coach_subtitle">Coach sui timing del tuo mazzo attivo</string>
    <string name="flow_coach_empty_title">Nessun mazzo attivo</string>
    <string name="flow_coach_empty_body">Apri o modifica un mazzo per vedere qui il suo coach sui timing.</string>
    <string name="flow_coach_cards_section_title">Carte in questo mazzo</string>
    <string name="flow_segoc_section_title">Trigger simultanei</string>
    <string name="flow_segoc_rule_text">Se questi si attivano insieme: tra i TUOI trigger scegli tu l\'ordine — vanno in chain per primi. Poi l\'avversario sceglie l\'ordine dei suoi — vanno dopo. La chain risolve a ritroso: quindi gli ultimi messi in chain si risolvono per primi.</string>
    <string name="flow_segoc_generic_note">Conviene di solito risolvere per ultimo l\'effetto che ti dà più informazione o scelta, per decidere con più dati.</string>
    <string name="flow_open_catalog">Sfoglia i flow HAT curati</string>
    <string name="flow_back_to_coach">Torna al coach</string>
```

- [ ] **Step 6: Build**

Run: `cd android && ./gradlew --no-daemon :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Emulator check (required — this task changes visible UI)**

Install on the `pixel_7_-_api_36_0` emulator, launch, navigate to the Flow tab. Confirm:
1. With no decks: empty state renders ("No active deck" / "Apri o modifica un mazzo...").
2. Create or open a deck with at least two monsters sharing an obvious trigger condition if possible (otherwise any deck) — confirm the card list renders with name + timing chip (or no chip, for cards with no SEGOC data — that's a valid "not yet classified" state, not a bug).
3. Tap "Browse curated HAT flows" — confirm the existing catalog screen still opens and its cards still open rehearsal correctly (regression check on the demoted-but-unchanged existing feature).
4. Tap back from the catalog — confirm it returns to the coach view, not to a blank screen or crash.
Screenshot each state via `adb exec-out screencap`.

- [ ] **Step 8: Commit**

```bash
git add android/feature/flow/src/main/kotlin/com/ygochecker/feature/flow/FlowScreen.kt android/core/designsystem/src/main/res/values/strings.xml android/core/designsystem/src/main/res/values-it/strings.xml
git commit -m "feat(android): Flow tab becomes an active-deck SEGOC timing coach"
```

---

## Task 9: Full verification pass

**Files:** none (verification only)

- [ ] **Step 1: TS side**

Run: `cd ygo-card-checker && npm run db:test:effects && npm run db:test:segoc`
Expected: both exit 0 with their `... OK` lines.

- [ ] **Step 2: Android full test suite**

Run: `cd android && ./gradlew --no-daemon testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all suites green including the new `SegocProfileTest`, `SegocDetectorTest`, `FindSimultaneousTriggersTest`.

- [ ] **Step 3: Release-shaped build**

Run: `cd android && ./gradlew --no-daemon :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Full emulator walkthrough**

Install on `pixel_7_-_api_36_0`, and in one session walk: Home → Flow (coach view with real cards) → open catalog → open a rehearsal → back → back to Home → Decklist (confirm nothing regressed from the badge/theme work of the prior redesign session) → Search (same). This is the single end-to-end pass tying together this task's work and the prior session's — confirm no interaction/regression between the two.

- [ ] **Step 5: Update PROGRESS.md**

Add a `## Done` entry (top of the list, matching this repo's existing entry format) summarizing: SEGOC parser + whole-catalog extraction shipped, Flow tab is now the active-deck coach, HAT catalog demoted but functional. Note the branch name (`refactor/segoc-flow-coach`) and reference both spec files.

- [ ] **Step 6: Commit**

```bash
git add PROGRESS.md
git commit -m "docs: update PROGRESS.md for SEGOC Flow Coach"
```
