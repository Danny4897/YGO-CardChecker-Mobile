# MCP Status — Battle Simulation

Last check: 2026-07-11 (updated)

## Summary

| Server | Status | Notes |
|--------|--------|-------|
| harness | OK | `harness validate` passes |
| context7 | OK | `libraryName` + `query` |
| **yugioh** | **OK** | `search-cards` with `fname` verified |
| sequential-thinking | OK | Listed in catalog |
| telegram | ERROR | Not required |

## yugioh — verified

```text
search-cards { fname: "Blue-Eyes White Dragon" } → 3 results (89631139, ...)
```

DB fix still in place: `npx prisma db push` in `tools/yugioh-mcp-server`.

## harness

CLI `harness validate` passes. MCP `validate_project` still has path error — use CLI.
