# Loop state — YGOChecker

## Open

- **SEGOC Flow Coach** (branch `refactor/segoc-flow-coach`, spec `docs/superpowers/specs/2026-08-21-segoc-flow-coach-design.md`) —
  in corso: estende Combo Assist con dati SEGOC reali (Spell Speed, tipo effetto, rischio Missed Timing, trigger
  event) estratti da Lua, motore di rilevamento trigger simultanei sul mazzo attivo, Flow tab come coach
- **AI Complete deck** — oggi rule/synergy + text/LUA profiles; futuro modello su mazzi pubblici + replay
- **Nuove feature DB-powered** — lista spesa mazzo↔collezione, meta snapshot mazzi pubblici, alert banlist
  schedulati (schema `user_alerts`/`banlist_snapshots` già pronto lato Supabase, non ancora popolato)
- OAuth Discord/Google reale via Supabase Auth (oggi PKCE locale con client secret nell'app — noto problema
  di sicurezza, rimandato: richiede che l'utente crei le app OAuth e configuri i redirect URL lui stesso)
- **Cloud sync** profilo/amici/mazzi/replay keyed by Discord/Google subject

## Done

- 2026-08-21: **Reimagining pass** (branch `claude/app-improvements-ux-xzo4yw`, commit 698814c..8711a8e) —
  navigazione reale con back-stack (BackHandler in MainActivity + ProfileRoute, Overlay spostato nel drawer),
  nuova tab **Home** (`feature:home`, dashboard con AlertBell/legalità ultimo mazzo/scorciatoie), **Flow
  spedito** (rimosso il gate Coming Soon, il motore hand-test + analizzatore ruoli era già completo), range
  ATK/DEF reale in Search, ricerca/ordinamento nella lista mazzi, editor profilo su ModalBottomSheet a piena
  altezza invece di un AlertDialog compresso. Vedi il commit sopra per il dettaglio backend Supabase.
- 2026-08-21: **Social backend → Supabase** — sostituito il server Node/SQLite (PC + tunnel Cloudflare, mai
  affidabile oltre il beta personale) con un progetto Supabase gestito (Postgres + Auth + Realtime, RLS su
  tutte le tabelle, advisor di sicurezza pulito). Nuovo modulo `data:social`
  (`io.github.jan-tennert.supabase` 3.2.6) sostituisce `HttpSocialRepository`; stesse firme per
  Profile/Decklist/Settings, nessuna UI toccata oltre a Settings (URL backend testuale → sezione Account con
  magic-link email, dato che l'identità non era più recuperabile su reinstall). Verifica: schema/RLS testati
  via MCP Supabase; **build/test Kotlin non eseguibile in questo sandbox** — `dl.google.com` (repo Maven di
  AGP) è bloccato dal proxy di rete, quindi zero compilazione verificata, nemmeno sui moduli JVM puri. Passi
  manuali richiesti prima del primo run: abilitare "Anonymous sign-ins" e aggiungere
  `ygochecker://oauth/magiclink` ai redirect URL in Authentication → dashboard Supabase (nessun tool MCP
  per farlo). Vedi `android/README.md`.
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
- 2026-08-17: **Verifier (Combo Assist USP)** — PASS Android (JBR 21): model/domain/cards debug **49/49** (ComboAssistEngine 6, ComboAssistUseCases 2, FlowPackParser 2); `assembleDebug` APK ~70MB; DI binds + `CardDetailState.comboLines` OK. harness validate FAIL (AGENTS.md sections, pre-existing). npm Headless **53/53** + build OK
- 2026-08-17: **Combo Assist USP** — ComboAssistEngine (key cards, choke, recovery copies); flows-hat chokePoints; card detail combos + menu Analizza combo mazzo
- 2026-08-16: **Complete + Genera Flow (C)** — CompleteDeck usa Flow/roles; Extra toolbox format-aware; menu Genera linee Flow; link deck→flow; Flow tab mostra collegate
- 2026-08-16: **Verifier (Overlay HAT D)** — PASS Android 60/60; TimingRuleEngineTest 4/4
- 2026-08-16: **Overlay HAT assist (D)** — tips + turn toggle + resource chips
- 2026-08-16: **Flow HAT v1** — catalog + rehearsal + seed
- 2026-08-16: **v0.3.3** — fix click mazzi pubblici → thread
- 2026-08-16: **v0.3.2** — fix ricerca utenti (sessione + feedback)
- 2026-08-16: **Verifier** — Android `gradlew test` PASS (JBR 21); :app / :core:* / :data:* OK; no failures
- 2026-08-16: **v0.2.9** — feed aggiornamenti su jsDelivr (fix cache raw GitHub ~5 min)
- 2026-08-16: **v0.2.8** — update check: cache-bust feed + manual ripropone skip; bump per chi aveva “Più tardi” su 0.2.7
- 2026-08-16: **v0.2.7** — shell: drawer non copre navbar; swipe tra tab primari
- 2026-08-16: **v0.2.6** — YDKE: fallback per-id se batch YGOPRODeck 400; Compose forge solo post-success
- 2026-08-16: **v0.2.5** — fix YDKE false offline; ensure HAT pack before resolveByIds
- 2026-08-16: **v0.2.4** — fix FOSS update install (FileProvider + progress + post-download hints)
- 2026-08-16: **v0.2.3** — splash Duel Disk/ologramma; import shuffle; what's-new post-update
- 2026-08-16: **v0.2.2** — dialog update a tema duel; `assembleRelease` non-debuggable (Play Protect); PackageInstaller
- 2026-08-16: **v0.2.1** — Profile bottom bar senza tab Profile; sezione Discord/Google nascosta (OAuth dopo)
- 2026-08-16: **Navbar Profile** — bottom bar visibile su Profile **senza** tab Profile; Discord non apre più l'app Discord fake
- 2026-08-16: **v0.2.0** — emblem stilizzati; OAuth UX; FOSS update feed
- 2026-08-15: **FOSS auto-update** — feed `update.json` + install all'avvio / Settings; docs in `android/distribution/`
- 2026-08-15: **Verifier** — Android unit tests PASS; spot-check overlay/CardDetail/Extra OK; npm test 106 OK + build OK; harness validate FAIL (AGENTS.md sections, pre-existing)
- 2026-08-15: **Complete banner** non-blocking + staple Extra **opzionali** (chip nel dialog Completa)
- 2026-08-15: **UI polish** — DuelWorkingOverlay su Completa mazzo; splash/forge più lunghi; dettaglio decklist = search + salva collezione con nome
- 2026-08-15: **Complete Extra** — skip Fusion named/contact (Armityle/Barbaroid); staple toolbox (101/Exciton/Castel…); Fusion soft solo se poly/hero
- 2026-08-15: **Complete Extra diversity** — 1-of only; bilancia Synchro/Xyz/Fusion/Link; Xyz boost se Main ha 2+ stesso livello
- 2026-08-15: **Text synergies for all cards** — race/type from effect text; package pairs; related non più solo static JSON
- 2026-08-15: **Fix related** — stop remote 35MB related.json wipe; HAT pack + auto-heal se edge < 5k; GetRelated fallback legalità
- 2026-08-15: **YDKE** padded Base64; Completa mazzo cascade + Side
- 2026-08-15: Flow Coming Soon; drawer brandato; related enrichment v1

## Notes

- JDK via Android Studio `jbr`
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Prima di Completa mazzo: Impostazioni → **Scarica tutto**
- Feed update: `android/distribution/update.json` su `main`
- `ng test` plain può hangare in watch / Electron disconnect; usare `--watch=false --browsers=ChromeHeadless`
- **USP deck coach:** apri una carta nel mazzo → sezione Combo; oppure menu → **Analizza combo mazzo**
