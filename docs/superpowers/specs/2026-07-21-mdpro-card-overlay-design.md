# MDPRO Card Overlay — Design

**Branch:** `refactor/mdpro-card-overlay` (parent + `ygo-card-checker`)  
**Date:** 2026-07-21

## Goal

When the user views a card detail in MDPRO (or Master Duel on phone), a light companion in YGOChecker reads the on-screen card identity and shows the same legality / related / combo info as the site checker.

## Locked decisions

| Topic | Choice |
|-------|--------|
| Delivery | Web page in the existing Angular SPA (`/overlay`) — no Electron/Tauri |
| Platforms | Desktop + phone (PWA-friendly) |
| Activation | Hybrid: live screen capture on desktop; one-shot image on phone |
| Content | Checker-equivalent tabs (legality + related + combo) |
| Lookup | Prefer passcode from OCR text → `getCardById$`; else cleaned name → `resolveCardByName$` / search |
| OCR engine | `tesseract.js` via dynamic import (not in initial bundle) |
| Konami API | Not used; YGOPRODeck + local knowledge JSON (same as site) |

## Architecture

```
OverlayPage
  ├─ ScreenCaptureService   getDisplayMedia / file→ImageBitmap
  ├─ CardScreenOcrService   tesseract (lazy) → raw text
  ├─ parseMdproOcrText()    passcodes + candidate name (pure)
  ├─ OverlayStore           resolve card → reuse CheckerStore patterns
  └─ CardDetailTabs         existing UI
```

### Desktop live

1. User starts “Live” and shares the MDPRO window (or entire screen).
2. Every ~1.5s a frame is sampled from a crop region (center title bar by default; user can nudge crop).
3. OCR → parse → if identity changed, lookup and update panel.
4. Stop sharing ends live mode.

### Phone one-shot

1. User picks/takes a photo or pastes a screenshot.
2. Single OCR + lookup.
3. Manual name field always available as correction.

## Error handling

Result-style + `errorKey` i18n (no throws for user flow):

- `overlay.error.captureUnsupported`
- `overlay.error.ocrFailed`
- `overlay.error.cardNotFound`
- `overlay.error.api`

## Out of scope (v1)

- Injecting into MDPRO process
- Always-on-top native window
- Auto-detect MDPRO without user share permission
- Writing deck changes from overlay

## Acceptance

1. `/overlay` reachable from desktop and mobile nav  
2. Desktop: live share → Blue-Eyes detail screenshot resolves to Blue-Eyes White Dragon  
3. Phone: upload same screenshot → same result  
4. Detail tabs show legality / related / combo for selected format  
5. Manual name search works if OCR misses  
6. `tesseract.js` not in initial JS budget (lazy chunk)  
7. Unit tests for `parseMdproOcrText`  
