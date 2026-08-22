# SEGOC Field + Puzzle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the MDPRO overlay, add MDPro3-layout FieldView with runtime art, SEGOC step lessons from the active deck, and template puzzles vs importable opponent decks.

**Architecture:** Pure Kotlin in `core:model` (`buildSegocLessons`, `instantiatePuzzles`, `evaluatePuzzle`, `MdproAssetPaths`). Room flag `isPuzzleOpponent` on decks. Compose Field/Puzzle in `feature:flow`. Overlay module removed from Gradle and nav.

**Tech Stack:** Kotlin, JUnit4, Jetpack Compose, Room (`fallbackToDestructiveMigration`), Coil, DataStore.

## Global Constraints

- No `ocgcore`, no vendoring MDPro3 `Picture/` into git/APK.
- Art: runtime `Picture/Art/{passcode}.jpg` or YGOPRODeck `imageUrl` fallback.
- `AppResult` / `errorKey` for user errors; no throws for business flow.
- Android minSdk 26, JVM 17. Tests: `cd android && ./gradlew :core:model:test :core:domain:test --offline` when possible.
- Branch: `refactor/segoc-tmm-field`. Do not commit unless the user asks.
- OverlayAssist / TimingRuleEngine stay (HAT tips + unit tests); only `:feature:overlay` dies.

---

### Task 1: SEGOC lesson builder (pure)

**Files:**
- Create: `android/core/model/src/main/kotlin/com/ygochecker/core/model/SegocLesson.kt`
- Create: `android/core/model/src/test/kotlin/com/ygochecker/core/model/SegocLessonBuilderTest.kt`

**Produces:** `buildSegocLessons(yourIds, oppIds, profiles): List<SegocLesson>`

- [ ] Tests then impl (TDD). Emit lesson if ≥2 your TRIGGER on same non-OTHER event, or ≥1 your + ≥1 opp. Skip OPP_ORDER when opp empty. OTHER never lessons.

---

### Task 2: Puzzle instantiate + evaluate (pure)

**Files:**
- Create: `android/core/model/src/main/kotlin/com/ygochecker/core/model/PuzzleModels.kt`
- Create: `android/core/model/src/test/kotlin/com/ygochecker/core/model/PuzzleInstantiatorTest.kt`

**Produces:** `instantiatePuzzles(...)`, `evaluatePuzzle(instance, placed): Boolean`

Templates: `lifo_your_two` (expectedChain `[lowId, highId]`), `apnap_you_vs_opp`, `missed_timing_when`. Cap 12.

---

### Task 3: MDPro3 asset relative paths (pure)

**Files:**
- Create: `android/core/model/src/main/kotlin/com/ygochecker/core/model/MdproAssetPaths.kt`
- Create: `android/core/model/src/test/kotlin/com/ygochecker/core/model/MdproAssetPathsTest.kt`

**Produces:** `MdproAssetPaths.cardArt(passcode) == "Picture/Art/{id}.jpg"`

---

### Task 4: Deck `isPuzzleOpponent` + settings path

**Files:**
- Modify: `Models.kt` Decklist, `Domain.kt` DeckRepository + `SetDeckPuzzleOpponent`, fakes in domain tests
- Modify: `DeckData.kt` entity column, DAO, version 6, mapping
- Modify: `DependencyModule.kt` bind use case
- DataStore key `mdpro_root` on `PreferenceRepository` **or** dedicated `MdproAssetSettings` (prefer dedicated to avoid fake churn — bind DataStore impl)

---

### Task 5: Delete overlay module

**Files:** remove `:feature:overlay`; `settings.gradle.kts`; `app/build.gradle.kts`; `MainActivity.kt`; `SettingsScreen.kt`; overlay HUD strings; overlay permissions in app manifest.

Keep `overlay_tip_*` strings (TimingRuleEngine keys).

---

### Task 6: FieldView + Flow coach lessons + Puzzle runner

**Files:**
- Create: `android/feature/flow/.../FieldView.kt`, `PuzzleScreen.kt`
- Modify: `FlowScreen.kt`, `feature/flow/build.gradle.kts` (coil)
- Strings en+it for lessons/puzzle/mdpro folder
- Seed: `android/data/cards/src/main/assets/puzzle-opponents/hat-backrow.txt` and `empty-control.txt`

---

### Task 7: Verify

```
cd android && .\gradlew :core:model:test :core:domain:test :feature:flow:test :app:assembleDebug
```

Then emulator install + smoke Flow / Puzzle.
