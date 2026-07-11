---
name: ygo-checker
description: YGOChecker domain rules for deck import/export (text list, YDKE), YGOPRODeck API resolution, Angular decklist editor, and UI conventions. Use when working on ygo-card-checker decklists, card search, legality, or import/export features.
---

# YGOChecker

Project path: `ygo-card-checker/` (Angular 20, Tailwind, DaisyUI theme `duel`).

## Deck text list format

- One card per line: `quantity name` (e.g. `2 Summoner Monk`, `3x Maxx "C"`)
- Section markers: `#main`, `#extra`, `#side` (optional)
- Without markers: all lines go to Main; Extra Deck monsters auto-move to Extra by card type
- Import resolves names: primary UI language (`it`/`en`) then fallback language (exact match only)

## YDKE

- Format: `ydke://<base64_main>!<base64_extra>!<base64_side>!`
- Passcode-based; import uses EN API names
- Core: `src/app/services/ydke.service.ts`, `DecklistStore.importFromYdke$()`

## Text list implementation

- Parse/format: `src/app/services/deck-text.service.ts`
- Name resolution: `YgoApiService.resolveCardByName$()`
- Store: `DecklistStore.importFromText$()`, `exportDeckText$()`
- UI: `text-deck-dialogs.component.ts`, header menu Import/Export

## Architecture notes

- Single format selector in app shell (`FormatStore`); mobile fallback in checker/decklist editor
- Deck state: `DecklistStore` (signals) + `DecklistService` (pure CRUD)
- Branch prefix for structural work: `refactor/`
- Do not throw for business flow; use `Result`-style observables with `errorKey` i18n keys

## Verification

- Unit tests: `deck-text.service.spec.ts`, `ydke.service.spec.ts`
- Run: `npx ng test` / `npx ng build`
- Cross-check card names via YGO MCP `search-cards` / `get-cards` when validating import lists
