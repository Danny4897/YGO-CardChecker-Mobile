# Battle Simulation — Execution Plan

Branch: `refactor/battle-simulation`

## Goal

Interactive Yu-Gi-Oh duel simulator with:
- Structured game state engine
- Effect resolution from `CardKnowledgeEffect` index
- Duel-themed UI (daisyUI `duel` theme)

## Phases

### Phase 0 — Bootstrap ✅

- [x] MCP health check + `MCP-STATUS.md`
- [x] Git init + branch `refactor/battle-simulation`
- [x] Plan documents

### Phase 1 — Core Engine ✅

- [x] Models: `DuelState`, `CardInstance`, zones, phases
- [x] Pure `battle-engine.ts`: draw, summon, attack, LP, phases
- [x] `effect-interpreter.ts`: map knowledge effects → actions
- [x] Unit tests for engine + interpreter
- [x] Sample duel setup (demo decks)

### Phase 2 — UI Shell ✅

- [x] Route `/battle`
- [x] Battle page: field layout, LP, phase bar
- [x] Hand + zone interaction
- [x] Action log panel
- [x] Nav link + i18n (it/en)

### Phase 3 — Effect Activation ✅

- [x] Tribute resolution (auto-pick)
- [x] Spell/trap set & activate
- [x] S/T zones on field
- [x] Deck import from DecklistStore

### Phase 4 — Polish & AI ✅

- [x] Manual tribute selection UI
- [x] Chain stack (push + LIFO resolve)
- [x] Synchro / Xyz summon
- [x] Field spell zone + field actions panel
- [x] Opponent AI (`battle-ai.ts`)
- [x] Mobile nav tab for `/battle`

### Phase 5 — Verification ✅

- [x] `harness validate`
- [x] `npm test && npm run build`
- [x] Verifier subagent

## Live tracking

Update `PROGRESS.md` on each loop tick. Per-phase detail in `PHASE-*.md`.

## Acceptance (MVP)

1. Start demo duel from UI
2. Draw, Normal Summon, Battle Phase attack reduces LP
3. Card with parsed effect shows activatable action when legal
4. Action log explains effect in i18n keys
5. All tests green, build passes
