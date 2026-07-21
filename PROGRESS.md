# Loop state — YGOChecker

## Open

- `refactor/mdpro-card-overlay`: **VERIFY FAIL** — `npm run build` TS errors in `overlay.store.ts` (missing `applyOcrResult` method header ~562); tests 29/29 pass
- `refactor/deck-assistant`: Phase 2 — combo gap detection (WIP stashed)

## Done

- 2026-07-21: Verifier — `harness validate` CLI pass; `ng test --watch=false` 29 SUCCESS; **build FAIL** (overlay.store.ts syntax)
- 2026-07-21: Overlay baseline committed — full passcode catalog, spell OCR noise fix, qty×0 if not playable
- 2026-07-21: Overlay UX — PiP search-row layout; lang-aware refresh; passcode catalog + IndexedDB for near-instant lookup
- 2026-07-21: Overlay — restored working light-mode: full-frame OCR on detail open; PiP side rail; no in-detail fingerprint chase
- 2026-07-21: Overlay — card-switch OCR in-detail; PiP side rail ◀; probe keeps running when docked
- 2026-07-21: Overlay light mode — `pixelmatch` probe, OCR solo su open dettaglio, PiP close on close; TextDetector+tesseract lazy
- 2026-07-21: `refactor/mdpro-card-overlay` — `/overlay` live OCR + upload; 27 tests + build pass
- 2026-07-13: Branch `refactor/deck-assistant` from main
- 2026-07-13: Deck Coach MVP — analyzer, service, UI panel, Ollama narrative
- 2026-07-13: Unified deck assist chat — model, service, panel UI, i18n; verified (harness + 28 tests + build)

## Notes

- Branch: `refactor/mdpro-card-overlay` — OCR on detail open + on in-detail card change; collapsed PiP = side rail with ◀
- Deck-assistant WIP: stash on `refactor/deck-assistant`
- Battle simulation work in stash on `refactor/03-community-auth`
