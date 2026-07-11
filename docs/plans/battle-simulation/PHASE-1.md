# Phase 1 — Core Engine

Status: **done (v1)**

## Tasks

- [x] `duel-state.model.ts`
- [x] `effect-action.model.ts`
- [x] `battle-id.util.ts` — instance ID generator
- [x] `battle-engine.ts`
- [x] `effect-interpreter.ts`
- [x] `battle-setup.ts` — demo decks
- [x] `battle-engine.spec.ts`
- [x] `effect-interpreter.spec.ts`

## Engine actions (v1)

| Action | Function |
|--------|----------|
| `createDuel` | Shuffle decks, 5 cards opening hand, LP 8000 |
| `draw` | Active player +1 from deck |
| `advancePhase` | Phase chain + turn swap at End |
| `normalSummon` | Hand → monster zone (1 tribute if L5+) |
| `setMonster` | Hand → monster zone face-down DEF |
| `attack` | ATK vs ATK/DEF, direct if empty |
| `applyEffectAction` | Execute interpreter output |

## Notes

Engine is pure/immutable — every function returns `{ ok: true, state } | { ok: false, errorKey }`.
