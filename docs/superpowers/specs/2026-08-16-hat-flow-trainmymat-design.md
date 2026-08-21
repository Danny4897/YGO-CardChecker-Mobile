# HAT Flow + TrainMyMat Boost — Design

**Date:** 2026-08-16  
**Branch:** `refactor/hat-flow-trainmymat-boost`  
**Status:** Approved for implementation (user: piena autonomia)  
**Reference:** https://trainmymat.com/

## Goal

Portare le idee forti di TrainMyMat (combo branching, rehearsal, breakdown, active recall) in YGOChecker Android, adattate al formato **HAT** (timing, bait, Artifact OP turn, trap priority), partendo dallo slice **A→B**: schema concettuale + seed Flow + Rehearsal UI.

## Locked decisions

| Topic | Choice |
|-------|--------|
| Prima ship | Flow catalog + Rehearsal (branch tap) + seed HAT |
| Persistenza Flow v1 | Asset JSON (`flows-hat.json`) — Room tables in slice successivo |
| Ruoli tattici | Seed JSON `card-roles-hat.json` → boost Related quando format=HAT |
| Overlay tips / Quiz / Replay→Flow | Slice D/E/F dopo A→B stabile |
| Clone TMM collection/PDF/paywall | Out of scope |

## Architecture (v1)

```
flows-hat.json (assets)
    → AssetFlowCatalog (data/cards)
    → ListFlows / GetFlow (domain)
    → FlowScreen catalog + FlowRehearsalEngine (feature/flow)

card-roles-hat.json
    → HatRolePack (data/cards)
    → SynergyEnrichment / GetRelatedCards boost
```

## Acceptance (slice A→B)

1. Tab Flow non mostra più Coming Soon.
2. Lista Flow HAT curati (opening / bait / artifact / trap).
3. Rehearsal: tap avanti sul ramo default; scelta esplicita se più edge.
4. Timing gate su nodi `chain_link_1_only` (Hand miss timing).
5. Unit test su `FlowRehearsalEngine`.
6. Related HAT riceve boost da ruoli seed (smoke).

## Non-goals (questa ship)

- Parser `.yrp`, overlay tip live, quiz UI, tournament tracker.
