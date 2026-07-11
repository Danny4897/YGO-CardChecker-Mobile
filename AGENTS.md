# YGOChecker

Yu-Gi-Oh deck builder + legality checker.

## Layout

| Path | Role |
|------|------|
| `ygo-card-checker/` | Angular 20 SPA (main app) |
| `tools/yugioh-mcp-server/` | Card data MCP (`yugioh`) |
| `tools/card-knowledge-db/` | SQLite pipeline scripts |

## Key files

- Routes: `ygo-card-checker/src/app/app.routes.ts`
- Deck state: `features/decklist/stores/decklist.store.ts`
- Text import: `services/deck-text.service.ts`
- YDKE: `services/ydke.service.ts`
- Domain skill: `.cursor/skills/ygo-checker/SKILL.md`

## Conventions

- Deck text: `qty name`; sections `#main` `#extra` `#side`
- User-facing errors: Result observables + `errorKey` (no throw)
- Structural branches: `refactor/*`

## Verify

```bash
harness validate
cd ygo-card-checker && npm test && npm run build
```

## Harness

- Level: **basic** (hooks profile: standard; layers off until refactor)
- MCP: `harness` + `yugioh` in `.cursor/mcp.json`
- Loop state: `PROGRESS.md`
- Subagents: `explorer` (readonly), `verifier` (tests+validate)

## Token tips

- Card lookups → MCP `yugioh`, not raw SQLite
- Context7 for Angular/API docs when unsure of version
- Ignore: `dist/`, `.angular/`, `node_modules/` (see `.cursorignore`)
