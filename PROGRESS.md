# Loop state ? YGOChecker

## Open

- `refactor/hat-flow-trainmymat-boost`: **next** Quiz timing (E), Replay?Flow (F); expand Combo Assist catalog
- **Cloud sync** profilo/amici/mazzi/replay keyed by Discord/Google subject

## Done

- 2026-08-17: **Verifier (Combo Assist USP)** - PASS Android (JBR 21): model/domain/cards debug **49/49** (ComboAssistEngine 6, ComboAssistUseCases 2, FlowPackParser 2); `assembleDebug` APK ~70MB; DI binds + `CardDetailState.comboLines` OK. harness validate FAIL (AGENTS.md sections, pre-existing). npm Headless **53/53** + build OK
- 2026-08-17: **Combo Assist USP** � ComboAssistEngine (key cards, choke, recovery copies); flows-hat chokePoints; card detail combos + menu Analizza combo mazzo
- 2026-08-16: **Complete + Genera Flow (C)** ? CompleteDeck usa Flow/roles; Extra toolbox format-aware; menu Genera linee Flow; link deck?flow; Flow tab mostra collegate
- 2026-08-16: **Verifier (Overlay HAT D)** ? PASS Android 60/60; TimingRuleEngineTest 4/4
- 2026-08-16: **Overlay HAT assist (D)** ? tips + turn toggle + resource chips
- 2026-08-16: **Flow HAT v1** ? catalog + rehearsal + seed
- 2026-08-16: **v0.3.3** ? fix click mazzi pubblici ? thread
- 2026-08-16: **v0.3.2** ? fix ricerca utenti (sessione + feedback)
- 2026-08-16: **Verifier** ? Android `gradlew test` PASS (JBR 21); :app / :core:* / :data:* OK; no failures
- 2026-08-16: **v0.2.9** ? feed aggiornamenti su jsDelivr (fix cache raw GitHub ~5 min)
- 2026-08-16: **v0.2.8** ? update check: cache-bust feed + manual ripropone skip; bump per chi aveva �Pi� tardi� su 0.2.7
- 2026-08-16: **v0.2.7** ? shell: drawer non copre navbar; swipe tra tab primari
- 2026-08-16: **v0.2.6** ? YDKE: fallback per-id se batch YGOPRODeck 400; Compose forge solo post-success
- 2026-08-16: **v0.2.5** ? fix YDKE false offline; ensure HAT pack before resolveByIds
- 2026-08-16: **v0.2.4** ? fix FOSS update install (FileProvider + progress + post-download hints)
- 2026-08-16: **v0.2.3** ? splash Duel Disk/ologramma; import shuffle; what's-new post-update
- 2026-08-16: **v0.2.2** ? dialog update a tema duel; `assembleRelease` non-debuggable (Play Protect); PackageInstaller
- 2026-08-16: **v0.2.1** ? Profile bottom bar senza tab Profile; sezione Discord/Google nascosta (OAuth dopo)
- 2026-08-16: **Navbar Profile** ? bottom bar visibile su Profile **senza** tab Profile; Discord non apre pi� l'app Discord fake
- 2026-08-16: **v0.2.0** ? emblem stilizzati; OAuth UX; FOSS update feed
- 2026-08-15: **FOSS auto-update** ? feed `update.json` + install all'avvio / Settings; docs in `android/distribution/`
- 2026-08-15: **Verifier** ? Android unit tests PASS; spot-check overlay/CardDetail/Extra OK; npm test 106 OK + build OK; harness validate FAIL (AGENTS.md sections, pre-existing)
- 2026-08-15: **Complete banner** non-blocking + staple Extra **opzionali** (chip nel dialog Completa)
- 2026-08-15: **UI polish** ? DuelWorkingOverlay su Completa mazzo; splash/forge pi� lunghi; dettaglio decklist = search + salva collezione con nome
- 2026-08-15: **Complete Extra** ? skip Fusion named/contact (Armityle/Barbaroid); staple toolbox (101/Exciton/Castel?); Fusion soft solo se poly/hero
- 2026-08-15: **Complete Extra diversity** ? 1-of only; bilancia Synchro/Xyz/Fusion/Link; Xyz boost se Main ha 2+ stesso livello
- 2026-08-15: **Text synergies for all cards** ? race/type from effect text; package pairs; related non pi� solo static JSON
- 2026-08-15: **Fix related** ? stop remote 35MB related.json wipe; HAT pack + auto-heal se edge < 5k; GetRelated fallback legalit�
- 2026-08-15: **YDKE** padded Base64; Completa mazzo cascade + Side
- 2026-08-15: Flow Coming Soon; drawer brandato; related enrichment v1

## Notes

- JDK via Android Studio `jbr`
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Prima di Completa mazzo: Impostazioni ? **Scarica tutto**
- Feed update: `android/distribution/update.json` su `main`
- `ng test` plain pu� hangare in watch / Electron disconnect; usare `--watch=false --browsers=ChromeHeadless`
- **USP deck coach:** apri una carta nel mazzo ? sezione Combo; oppure menu ? **Analizza combo mazzo**
