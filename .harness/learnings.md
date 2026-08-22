# learnings

- 2026-07-11: harness bootstrap; layers off YGO (29 legacy violations)
- YGO card lookups: MCP yugioh, not SQLite dump
- MonadicSharp: zero NuGet deps in core

<!-- hash:ef181485 tags:systematic-debugging,fixed -->
- **2026-08-14 [skill:systematic-debugging] [outcome:fixed]:** Android cold start: replacing LocalContext with createConfigurationContext() breaks hiltViewModel (needs Activity in ContextWrapper chain). Fix: ContextWrapper(activity) overriding getResources/getAssets from localized config.
