# MDPRO Card Overlay Implementation Plan

> **For agentic workers:** Execute task-by-task. User requested full production in-session.

**Goal:** Web `/overlay` companion that OCR-reads MDPRO card detail and shows checker tabs.

**Architecture:** Pure OCR parse utils + lazy Tesseract + OverlayStore + OverlayPage reusing `CardDetailTabs` / legality + knowledge services.

**Tech Stack:** Angular 20, tesseract.js (dynamic), YGOPRODeck via `YgoApiService`, daisyUI `duel`.

## Global Constraints

- Branch prefix `refactor/`
- No throw for user paths — `errorKey` / Result-style
- Do not bloat initial bundle with tesseract
- Reuse existing card detail UI; no Electron

---

### Task 1: OCR parse utils + tests

**Files:**
- Create: `ygo-card-checker/src/app/utils/mdpro-ocr-parse.ts`
- Create: `ygo-card-checker/src/app/utils/mdpro-ocr-parse.spec.ts`

**Produces:** `parseMdproOcrText(text): { passcodes: number[]; candidateName: string | null }`

- [x] Implement + unit tests (Blue-Eyes MDPRO sample, noisy OCR, empty)

### Task 2: Capture + OCR services

**Files:**
- Create: `ygo-card-checker/src/app/services/screen-capture.service.ts`
- Create: `ygo-card-checker/src/app/services/card-screen-ocr.service.ts`
- Modify: `ygo-card-checker/package.json` (add `tesseract.js`)

### Task 3: Overlay store + page + route + i18n + nav

**Files:**
- Create: `ygo-card-checker/src/app/features/overlay/stores/overlay.store.ts`
- Create: `ygo-card-checker/src/app/features/overlay/pages/overlay.page.ts`
- Modify: `app.routes.ts`, `app-shell.component.ts`, `nav-icon.component.ts`, `it.json`, `en.json`

### Task 4: Verify

```bash
cd ygo-card-checker
npm test -- --no-watch --browsers=ChromeHeadless
npm run build
```
(parent) `harness validate` if available
