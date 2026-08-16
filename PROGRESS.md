# Loop state — YGOChecker

## Open

- `refactor/android-native`: **Flow redesign** — attuale motore nascosto dietro Coming Soon
- **AI Complete deck** — oggi rule/synergy + text/LUA profiles; futuro modello su mazzi pubblici + replay
- **Cloud sync** profilo/amici/mazzi/replay keyed by Discord/Google subject (android/backend/README.md
Profile as friend landing; public decks + world chat + DMs.

## Done

- 2026-08-16: **v0.3.3** � fix click mazzi pubblici ? thread
- 2026-08-16: **v0.3.2** � fix ricerca utenti (sessione + feedback)
- 2026-08-16: **Verifier** — Android `gradlew test` PASS (JBR 21); :app / :core:* / :data:* OK; no failures
- 2026-08-16: **v0.2.9** — feed aggiornamenti su jsDelivr (fix cache raw GitHub ~5 min)
- 2026-08-16: **v0.2.8** — update check: cache-bust feed + manual ripropone skip; bump per chi aveva “Più tardi” su 0.2.7
- 2026-08-16: **v0.2.7** — shell: drawer non copre navbar; swipe tra tab primari
- 2026-08-16: **v0.2.6** — YDKE: fallback per-id se batch YGOPRODeck 400; Compose forge solo post-success
- 2026-08-16: **v0.2.5** — fix YDKE false offline; ensure HAT pack before resolveByIds
- 2026-08-16: **v0.2.4** — fix FOSS update install (FileProvider + progress + post-download hints)
- 2026-08-16: **v0.2.3** — splash Duel Disk/ologramma; import shuffle; what’s-new post-update
- 2026-08-16: **v0.2.2** — dialog update a tema duel; `assembleRelease` non-debuggable (Play Protect); PackageInstaller
- 2026-08-16: **v0.2.1** — Profile bottom bar senza tab Profile; sezione Discord/Google nascosta (OAuth dopo)
- 2026-08-16: **Navbar Profile** — bottom bar visibile su Profile **senza** tab Profile; Discord non apre più l’app Discord fake
- 2026-08-16: **v0.2.0** — emblem stilizzati; OAuth UX; FOSS update feed
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
- Feed update: `android/distribution/update.json` su `main`
- `ng test` plain può hangare in watch / Electron disconnect; usare `--watch=false --browsers=ChromeHeadless`
