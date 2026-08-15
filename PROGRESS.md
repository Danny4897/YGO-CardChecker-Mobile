# Loop state — YGOChecker

## Open

- `refactor/android-native`: **Flow redesign** — attuale motore nascosto dietro Coming Soon
- **AI Complete deck** — oggi rule/synergy + text/LUA profiles; futuro modello su mazzi pubblici + replay
- OAuth bindings esterni (OK lasciati così)
- Sync cloud profilo/amici/mazzi pubblici

## Done

- 2026-08-15: **FOSS auto-update** — feed `update.json` + install all’avvio / Settings; docs in `android/distribution/`
- 2026-08-15: **Verifier** — Android unit tests PASS; spot-check overlay/CardDetail/Extra OK; npm test 106 OK + build OK; harness validate FAIL (AGENTS.md sections, pre-existing)
- 2026-08-15: **Complete banner** non-blocking + staple Extra **opzionali** (chip nel dialog Completa)
- 2026-08-15: **UI polish** — DuelWorkingOverlay su Completa mazzo; splash/forge più lunghi; dettaglio decklist = search + salva collezione con nome
- 2026-08-15: **Complete Extra** — skip Fusion named/contact (Armityle/Barbaroid); staple toolbox (101/Exciton/Castel…); Fusion soft solo se poly/hero
- 2026-08-15: **Complete Extra diversity** — 1-of only; bilancia Synchro/Xyz/Fusion/Link; Xyz boost se Main ha 2+ stesso livello
- 2026-08-15: **Text synergies for all cards** — race/type from effect text (Zombie World → Zombies + Paladin/Mezuki/…); package pairs; related non più solo static JSON
- 2026-08-15: **Fix related** — stop remote 35MB related.json wipe; HAT pack + auto-heal se edge < 5k; GetRelated fallback legalità
- 2026-08-15: **YDKE** padded Base64; Completa mazzo cascade + Side
- 2026-08-15: Flow Coming Soon; drawer brandato; related enrichment v1

## Notes

- JDK via Android Studio `jbr`
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Prima di Completa mazzo: Impostazioni → **Scarica tutto**
- Non commit finché non richiesto
- `ng test` plain può hangare in watch / Electron disconnect; usare `--watch=false --browsers=ChromeHeadless`
