# HAT Flow Rehearsal — Implementation Plan

> **For agentic workers:** Execute task-by-task. TDD on engine. No `--no-verify`.

**Goal:** Ship Flow catalog + branching rehearsal for HAT, with tactical role seed boosting Related.

**Architecture:** Pure `FlowGraph`/`FlowRehearsalEngine` in `core:model`; JSON assets + loaders in `data:cards`; domain ports; Compose UI in `feature:flow`.

**Tech Stack:** Kotlin, JUnit, org.json, Hilt, Compose, Room deferred.

## Global Constraints

- Branch: `refactor/hat-flow-trainmymat-boost`
- Language UI: IT + EN strings
- No destructive API changes to deck/legality
- `harness validate` + Android unit tests before claiming done

---

### Task 1: Flow domain types + rehearsal engine

**Files:**
- Create: `android/core/model/src/main/kotlin/com/ygochecker/core/model/FlowModels.kt`
- Create: `android/core/model/src/test/kotlin/com/ygochecker/core/model/FlowRehearsalEngineTest.kt`

- [ ] Engine: `start`, `options`, `choose`, `advanceDefault`, `timingGate`
- [ ] Test: Hand CL2 fails gate; default path walks; branch choice works

### Task 2: HAT seed assets + parsers

**Files:**
- Create: `android/data/cards/src/main/assets/offline-pack/flows-hat.json`
- Create: `android/data/cards/src/main/assets/offline-pack/card-roles-hat.json`
- Modify: `OfflinePackAssets.kt`
- Create: `FlowPackParser.kt`, `HatRolePack.kt`

### Task 3: Domain ports + DI

**Files:**
- Modify: `Domain.kt` — `ListFlows`, `GetFlow`
- Modify: `DependencyModule.kt`

### Task 4: Flow UI

**Files:**
- Rewrite: `FlowScreen.kt` — catalog + rehearsal (keep test-hand as secondary section)
- Modify: strings EN/IT

### Task 5: Related HAT role boost

**Files:**
- Modify: `SynergyEnrichment.kt` or `DefaultGetRelatedCards` / pack related merge
- Test: role relations appear for Fire Hand / Sanctum

### Task 6: Verify

- `cd android && ./gradlew test`
- verifier subagent
