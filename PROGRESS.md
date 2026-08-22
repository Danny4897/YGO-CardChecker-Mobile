# Loop state — YGOChecker

## Open

- **Trade tra amici** — esplicitamente rimandato dall'utente finché social/community non è potenziato
  (vedi voce sbloccante sopra); poi anche un flusso "fake" con CPU per testarlo lato UI senza un secondo account.
- **Camera scan v2** — v1 (sotto, Done) fa OCR solo sul primo text block per fuzzy-match nome; da valutare
  vero image-matching (art/foil) per i casi in cui l'OCR non arriva a un match affidabile.
- **Budget helper v2** — oggi 1 sostituzione per carta costosa via synergy graph (`GetRelatedCards`) filtrata
  per prezzo più basso; non tiene conto di ruolo funzionale (starter vs extender) né di combo breakage.
- **SEGOC**: direzione da confermare con l'utente — proposta discussa (non ancora implementata): smettere di
  puntare a un "solver" che genera linee combo automaticamente (spazio di ricerca intrattabile, nessun tool
  della community lo fa) e invece usare SEGOC come *arbitro* (ordine chain/APNAP/LIFO, missed timing) sopra
  una libreria di combo-recipe curate/community (starter → linea), matchate sulla mano reale in Flow.
- **BLOCCANTE social — abilitare "Anonymous sign-ins"** nel dashboard Supabase del progetto `ygochecker`
  (`ubflewrwtpbrbkjdohfx`) → Authentication → Providers, e confermare il redirect URL
  `ygochecker://oauth/magiclink` in Authentication → URL Configuration. Verificato via MCP Supabase
  (2026-08-22): RLS/schema/constraint su `public_decks`/`deck_messages` sono corretti (insert testato
  impersonando l'utente reale via JWT claim, riuscito), ma **zero righe sono mai state scritte** in
  `public_decks`/`deck_messages` e **zero identity `anonymous`** esistono in `auth.identities` — nessun
  publish è mai arrivato in fondo. Nessun tool MCP espone il toggle provider, va fatto a mano da dashboard.
- **AI Complete deck** — oggi rule/synergy + text/LUA profiles; futuro modello su mazzi pubblici + replay
- **Nuove feature DB-powered** — lista spesa mazzo↔collezione, meta snapshot mazzi pubblici, alert banlist
  schedulati (schema `user_alerts`/`banlist_snapshots` già pronto lato Supabase, non ancora popolato)
- OAuth Discord/Google reale via Supabase Auth (oggi PKCE locale con client secret nell'app — noto problema
  di sicurezza, rimandato: richiede che l'utente crei le app OAuth e configuri i redirect URL lui stesso)
- **Cloud sync** profilo/amici/mazzi/replay keyed by Discord/Google subject

## Done

- 2026-08-22: **Prezzo carte, valore mazzo, budget helper, Compagno da torneo, Scan v1** (branch
  `claude/app-innovation-ideas-t2s7fv`) — quattro feature nuove in un giro, **nessuna compilazione
  verificata in questo sandbox** (stesso limite di rete noto: `dl.google.com`/AGP bloccato dal proxy),
  quindi review del codice/PR prima di mergiare:
  - **Prezzo (Cardmarket via YGOPRODeck)**: `Card.priceEur`/`CardEntity.priceEur` (nullable, `CardDatabase`
    v5→6, cache re-scaricabile → wipe sicuro); `YgoProDeckApi.parseCardInfo` ora legge
    `card_prices[0].cardmarket_price`. Si popola per le carte che passano da ricerca/YDKE/**Scarica tutto**
    (Impostazioni) — quest'ultimo fa `insertAll` REPLACE su tutto il catalogo, quindi è il modo per
    retro-popolare i prezzi sui mazzi già esistenti; il pacchetto offline bundlato (`cards-hat.json.gz`)
    resta senza prezzo finché non gira un sync online. Prezzo mostrato in dettaglio carta, riga ricerca,
    badge "valore mazzo" (somma qty×prezzo, suffisso `+` se alcune carte non hanno ancora prezzo) nell'header
    Decklist.
  - **Budget helper**: nuovo use case `SuggestBudgetSwaps` (core:domain) — per le carte Main/Side più costose
    (>= 1€) cerca alternative via `GetRelatedCards` (synergy graph esistente) più economiche, non già in
    mazzo; menu Decklist → "Ottimizza budget" apre bottom sheet con swap 1-click.
  - **Compagno da torneo**: nuovo database Room **separato** `tournament.db` (`TournamentDatabase`, entità
    `DeckNotesEntity`/`TournamentMatchEntity`) — deliberatamente NON aggiunto a `DeckDatabase` esistente,
    che ha `fallbackToDestructiveMigration()` e contiene dati utente reali (mazzi/amici/collezioni): bumpare
    la sua versione avrebbe cancellato tutto agli utenti già installati. Menu Decklist → "Compagno da
    torneo" apre dialog full-screen con note forza/debolezza/strategia per mazzo, log incontri (round,
    avversario, risultato, W/L/D per game, side-deck tracker strutturato con chip picker Main→Side/Side→Main
    sulle carte reali del mazzo), e Duel Helper (life counter P1/P2 + chess clock, non persistito).
  - **Scan v1**: nuovo modulo Gradle `feature:scan` (CameraX, non presente prima — ML Kit text-recognition
    invece era già usato per l'overlay MDPro via screen-capture, riusato lo stesso client). OCR sul primo
    text block, fuzzy-match (Levenshtein) contro il catalogo offline via `CardRepository.search`, overlay
    live con nome/tipo/prezzo carta + tasto "Aggiungi a collezione". Raggiungibile da una nuova shortcut in
    Home ("Scansiona una carta"); nuova sezione `"scan"` nello state machine di `MainActivity` (stesso
    meccanismo di Overlay), niente drawer nuovo (quello esistente era già dead code/import inutilizzato).
    Permesso CAMERA dichiarato nel manifest del modulo (pattern già usato da `feature:overlay` per i suoi
    permessi). v1 limitato: matching sul solo nome (no vero image-matching), un solo text block (il più in
    alto, tipicamente dove sta il nome carta).
  - Punti di roadmap discussi ma NON implementati in questo giro (vedi **Open**): trade tra amici (dopo
    fix social), evoluzione SEGOC verso "arbitro + combo recipe" invece di solver, v2 di scan/budget helper.

- 2026-08-22: **Fix thread mazzo pubblico irraggiungibile** (branch `claude/app-innovation-ideas-t2s7fv`) —
  root cause diagnosticata via MCP Supabase: `DecklistScreen.setVisibility` scartava l'`AppResult` di
  `social.publishDeck`/`unpublishDeck`, quindi un publish fallito (es. sessione scaduta → fallback
  `signInAnonymously()` → provider anonimo disabilitato lato dashboard) lasciava comunque il flag locale
  `isPublic=true`, mostrando il mazzo come pubblico in Profile senza che la riga esistesse mai su
  `public_decks`. `ProfileScreen.openMyPublicDeck` poi apriva comunque il thread con un id "provvisorio"
  mai scritto (commento errato "getPublicDeck resolves it" — non lo fa, fa un lookup esatto per id), quindi
  `getPublicDeck` tornava sempre `social.deck_not_found`. Fix: `setVisibility` ora rollback il flag locale e
  mostra l'errore (snackbar `err:` esistente) se il publish/unpublish remoto fallisce; `openMyPublicDeck` non
  invoca più `onOpened` se il publish fallisce, mostra solo il notice. Aggiunto pulsante "Riprova" sul notice
  di `ProfileRoute` per re-invocare `bootstrap()` senza dover riavviare l'app. Resta bloccato dal toggle
  Supabase in cima a **Open** — senza quello nessun publish potrà comunque riuscire. **Build/test Kotlin non
  eseguibile in questo sandbox** (stesso limite di rete già noto: `dl.google.com`/AGP bloccato dal proxy),
  quindi nessuna compilazione verificata — solo lettura/coerenza di tipi contro il resto del file.

- 2026-08-21: **SEGOC Flow Coach** (branch `refactor/segoc-flow-coach`, spec `docs/superpowers/specs/2026-08-21-segoc-flow-coach-design.md`,
  plan `docs/superpowers/plans/2026-08-21-segoc-flow-coach.md`) — Flow tab passa da catalogo HAT curato a coach sul
  mazzo attivo. Nuova pipeline `segoc-parser.ts` (ygo-card-checker/tools/card-knowledge-db) estrae per l'intero
  catalogo (13.397 carte, verificato su script Lua reali EDOPro/MDPro3) effectType/spellSpeed/missedTimingRisk/
  triggerEvents, wired nel loop esistente `build-effect-scripts.ts`. Asset bundlato Android `segoc-profiles.json.gz`
  (85KB), nuova tabella Room + porte dominio (`GetSegocProfiles`, `FindSimultaneousTriggers`), detector puro per
  coppie di carte con trigger simultanei nello stesso mazzo, avviso con regola SEGOC reale (APNAP + LIFO). Catalogo
  HAT + rehearsal restano, retrocessi a sezione secondaria ("Browse curated HAT flows"), invariati. Verificato
  end-to-end su emulatore con mazzo reale importato (Effect Veiler → badge "SP2 QUICK", corretto). Build/test
  verdi su entrambe le codebase (TS + Kotlin). Vedi il ledger SDD in `.superpowers/sdd/progress.md` per il
  dettaglio task-per-task e review.
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
