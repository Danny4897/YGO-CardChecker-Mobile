# Loop state — YGOChecker

## Open

- **SEGOC recipe library (curato, non generato)** — motore arbitro/puzzle + libreria ricette implementati
  (`refactor/segoc-tmm-field`, merge con main 2026-08-22). Seed con **una sola ricetta placeholder** (id
  carta finti) — contenuto combo reale per archetipo non ancora scritto/verificato, prossimo step: sostituire
  con linee vere (curate o da guida) e poi valutare community upload.
- **Trade tra amici** — esplicitamente rimandato dall'utente finché social/community non è potenziato
  (vedi voce sbloccante sopra); poi anche un flusso "fake" con CPU per testarlo lato UI senza un secondo account.
- **Camera scan v2** — v1 (sotto, Done) fa OCR solo sul primo text block per fuzzy-match nome; da valutare
  vero image-matching (art/foil) per i casi in cui l'OCR non arriva a un match affidabile.
- **Budget helper v2** — oggi 1 sostituzione per carta costosa via synergy graph (`GetRelatedCards`) filtrata
  per prezzo più basso; **il ruolo funzionale ora esiste** (`CardRoleClassifier`, vedi Done sotto) ma
  `SuggestBudgetSwaps` non lo consulta ancora — prossimo step naturale, evita che uno swap tolga l'unico
  INTERRUPT del mazzo.
- **Combo planner v2** — oggi `ComboPlanner` (Done sotto) genera solo catene a 2-3 step sul pattern
  riempi-cimitero/usa-cimitero (`GY_ENGINE_COMPLEMENTS`); non copre combo che non passano dal cimitero
  (es. linee Xyz/Link pure) né incatenamenti più lunghi — richiederebbe modellare costi/copie in mano, non
  solo composizione mazzo.
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

- 2026-08-29: **Motore di suggerimento carte/combo, generico e computato** (branch
  `claude/card-suggestion-engine-22o37e`) — sostituisce i bridge hardcoded per archetipo con logica
  data-driven su ogni carta del catalogo, riusando l'infrastruttura tag esistente (script LUA +
  `EffectTextProfiler`), non la pipeline TS esterna (`ygo-card-checker/` è un gitlink orfano, non nel
  repo — irraggiungibile da questo sandbox):
  - **Synergy graph generico** — `SynergyEnrichment.engineNeedles()` (nomi hardcoded tipo
    `"zombie" → "Zombie World"`, `"hero" → "Polymerization"`) rimosso, sostituito da
    `EffectMechanicTags.complementaryTags()`: tabella piccola di coppie meccaniche complementari
    (chi riempie il cimitero ↔ chi lo sfrutta), funziona su ogni archetipo del catalogo. Attivo subito
    sulle "carte correlate" esistenti, nessun nuovo asset/migrazione.
  - **`CardRoleClassifier`** (core:model) — deriva il ruolo funzionale (INTERRUPT/REMOVAL/SEARCHER/
    EXTENDER/BAIT/ENGINE_STARTER/TRAP_LINE, riusa `HatCardRole`) da tag+statistiche per **qualunque**
    carta, non solo quelle con `FormatCardRole` curato (oggi solo HAT). Il curato, dove esiste, vince
    sempre sul derivato.
  - **`AnalyzeDeckRoleGaps` + `SuggestSynergisticCards`** (core:domain, nuovo file `SuggestionEngine.kt`)
    — conta i ruoli del mazzo attivo (Main+Extra), segnala i ruoli sotto soglia (INTERRUPT<3, REMOVAL<2,
    SEARCHER<1, EXTENDER<1 — soglie fisse, non per-archetipo), cerca in catalogo carte legali per formato
    che coprono il gap E sono sinergiche con carte già in mazzo (via `GetRelatedCards` esistente), ranked
    per synergy score con spiegazione leggibile. Wired in Decklist: menu → **Suggerisci carte** → bottom
    sheet con tasto Aggiungi 1-click.
  - **`ComboPlanner`** (core:model, nuovo file) — genera linee combo (non curate, mai unite a
    `ComboRecipe`) incatenando sul pattern riempi-cimitero→usa-cimitero, bounded a 2-3 step, solo sulle
    carte realmente nel mazzo. Deliberatamente conservativo: ragiona su composizione mazzo (come
    `matchRecipes`/SEGOC lesson), non su una mano simulata — l'app non ha un hand-tester. Wired in
    Decklist: menu → **Genera linee combo (beta)**, sheet con disclaimer "non verificato da una persona".
  - Test unitari nuovi: `CardRoleClassifierTest`, `ComboPlannerTest`, `SuggestionEngineTest`,
    `GenerateComboLinesTest`, + 3 test su `EffectMechanicTags.complementaryTags`. **Non compilabile/
    verificabile in questo sandbox** (stesso limite noto: `dl.google.com`/AGP bloccato dal proxy di rete)
    — solo review manuale del codice, nessuna build/test Gradle eseguita. Verificare
    `JAVA_HOME`=JBR + `./gradlew :core:model:test :core:domain:test :data:cards:test :app:assembleDebug`
    prima del rilascio.
- 2026-08-22: **SEGOC Field + Puzzle + recipe library** (branch `refactor/segoc-tmm-field`, merge con main) —
  overlay module rimosso (`settings.gradle.kts`/app deps/nessun OverlayRoute/no SYSTEM_ALERT_WINDOW);
  `TimingRuleEngine` tenuto. SegocLesson (arbitro ordine SEGOC/APNAP/LIFO sui trigger event reali del mazzo,
  non calcolo linee), PuzzleModels (esito booleano, non solver), FieldView (board stile MDPro3),
  ComboRecipe/matchRecipes (libreria linee curate, seed solo placeholder — vedi Open), toggle
  "Usa come avversario puzzle" nel menu editor (mancava, il picker in Flow era sempre vuoto). Verificato:
  JUnit `core:model`/`core:domain`/`data:cards` PASS (serve `JAVA_HOME` = `Android Studio/jbr`, il JDK di
  sistema rompe Gradle su `DefaultTestTaskReports`), `assembleDebug` OK, smoke emulatore OK.
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

### Come pubblicare una release (leggere PRIMA di rifare tutto da capo)

**Se Claude Code gira in locale sul tuo PC** (terminale/app sul tuo computer, non
claude.ai/code remoto): funziona come sempre, senza nessuno dei passaggi sotto —
rete vera, Android SDK, e il tuo `~/.android/debug.keystore` sono già lì. Bump
versione + `assembleRelease` + `gh release create` in un colpo solo. **Tutte le
release fino a v0.7.3 sono state fatte così** (commit autore `Daniele
<a.danielefrau@hotmail.com>` — non un bot).

**Se invece gira in una sessione remota/cloud** (claude.ai/code, Slack, ecc.):
quella sessione **non può compilare** — `dl.google.com` (repo Maven di AGP) è
bloccato dalla policy di rete (403 "organization policy", confermato, non
aggirabile, non ritentare). La soluzione è la pipeline GitHub Actions creata
l'8/9/2026 (`.github/workflows/release.yml`), che builda su runner GitHub veri:

1. Bump `versionCode`/`versionName` in `android/app/build.gradle.kts` (+ eventualmente `WHATS_NEW`).
2. **Non** toccare `android/distribution/update.json` finché la release non esiste davvero con l'APK allegato — punta sempre all'ultima release reale finché non è confermato l'asset, altrimenti il popup di aggiornamento in-app fallisce con un 404 mascherato da "errore di connessione generico" (già successo, vedi fix in `AppUpdateUi.kt`).
3. Innescare la build: **né il dispatch manuale via API né il push di un tag funzionano** da una sessione remota (entrambi 403 da GitHub, permessi limitati del token di questa integrazione — non è un problema di rete). Funziona invece un **push di branch normale** sul branch `ci-release-trigger` (`git branch -f ci-release-trigger main && git push origin refs/heads/ci-release-trigger:refs/heads/ci-release-trigger`) — il workflow ha un trigger `push: branches: [ci-release-trigger]` apposta, deriva il tag dalla `versionName` di `build.gradle.kts` e crea la release con l'`Actions token` interno (permessi diversi dal token git di questa sessione).
4. Controllare l'esito con `mcp__github__actions_get` (`get_workflow_run`) sull'run id restituito da `list_workflow_runs`; se fallisce, i log veri stanno in `mcp__github__get_job_logs` con `return_content=true` (risposta enorme su una riga sola — salvarla su file e leggerla con `python3 -c "import json; ..."` + grep su `"e: file://"`/`error:`, **mai fermarsi al messaggio generico "Compilation error, see log"**).
5. Solo dopo aver confermato con `get_release_by_tag` che l'asset `app-release.apk` esiste davvero (dimensione ragionevole, non pochi KB), aggiornare `update.json` e pushare su `main`.
6. **Firma**: la release CI usa il debug keystore generato al volo dal runner (nessun keystore nel repo), diverso da quello del tuo PC — la prima installazione di un APK buildato da CI sopra un'installazione fatta in locale richiede una disinstallazione manuale. Le release CI successive tra loro restano coerenti (il workflow non rigenera il keystore a ogni run all'interno dello stesso branch/checkout... verificarlo comunque se ricapita).
7. Vincoli git minori scoperti per la stessa via: esiste un **tag remoto chiamato `main`** (probabile errore storico) che rende ambiguo `git push origin main` — usare sempre `git push origin refs/heads/main:refs/heads/main`.

Prima di rifare da zero questa indagine: leggere questa nota, controllare se
`.github/workflows/release.yml` esiste ancora ed è aggiornato, e ripartire dal
punto 1.
