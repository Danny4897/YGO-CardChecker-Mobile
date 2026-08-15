# UX mobile-first per YGOChecker Android

Questa guida traduce il core loop di YGOChecker in un'esperienza Android nativa. Il tema web `duel` è una fonte di identità visiva, non un modello di layout. La priorità è completare ricerca, modifica e controllo del mazzo con una mano, anche offline e con tecnologie assistive.

## Principi operativi

1. **Il mazzo è il contesto di lavoro.** Ricerca e dettaglio carta devono sempre mostrare in quale mazzo si aggiunge e quante copie sono già presenti.
2. **Le azioni essenziali restano visibili.** Quantità, sezione, legalità e annullamento non dipendono da gesture nascoste.
3. **Feedback immediato, persistenza affidabile.** Aggiornare subito la UI, salvare in background e ripristinare lo stato dopo process death; in caso di errore, fare rollback e spiegare come recuperare.
4. **La legalità guida, non blocca in silenzio.** Mostrare limite e motivo vicino alla carta; impedire solo l'azione impossibile e offrire un percorso utile.
5. **Offline è uno stato normale.** Mazzi esistenti e catalogo seed restano usabili; immagini e sincronizzazione degradano senza interrompere il flusso.

## Architettura di navigazione

### Destinazioni principali

Usare una `NavigationBar` Material 3 con tre destinazioni:

- **Cerca**: catalogo, cronologia e dettaglio carta.
- **Mazzi**: elenco mazzi e editor.
- **Impostazioni**: formato, lingua, sincronizzazione e futuro pacchetto offline.

Regole:

- Mostrare barra inferiore solo sulle tre destinazioni root. I dettagli possono mantenerla se non sottrae spazio al contenuto; l'editor del mazzo può nasconderla quando la tastiera è aperta.
- Un tap sulla destinazione già selezionata torna alla root e, al secondo tap, riporta la lista all'inizio.
- Ogni destinazione conserva query, scroll e selezione quando si passa a un'altra tab.
- Il pulsante Back chiude prima tastiera, sheet o selezione contestuale, poi risale lo stack. Dalla root segue il comportamento Android standard.
- Link, notifiche e import aprono direttamente il contenuto pertinente e mantengono un percorso Back comprensibile.
- Su finestre medium/expanded si può sostituire la barra con `NavigationRail`; non introdurre layout tablet finché il flusso phone non è completo.

### Top app bar

- **Root**: titolo breve e una sola azione contestuale ad alta frequenza.
- **Editor**: back, nome mazzo, stato di salvataggio solo quando rilevante, overflow per rinomina, import/export ed elimina.
- **Dettaglio carta**: back, nome carta troncato a una riga, azione aggiungi.
- Evitare `LargeTopAppBar` nelle schermate dense come editor e risultati; usare la variante standard o medium solo nelle root.

## Elenco mazzi

- Mostrare righe o tile a larghezza piena con nome, conteggio Main/Extra/Side, formato e ultima modifica.
- Il FAB **Nuovo mazzo** è appropriato nella root Mazzi; dopo lo scroll può ridursi alla sola icona.
- Tap apre il mazzo. Pressione prolungata entra in modalità selezione per rinomina, duplica, esporta o elimina.
- Eliminazione sempre con conferma nominativa. Dopo la conferma mostrare `Snackbar` con **Annulla**.
- Non richiedere una copertina. Se disponibile, l'artwork è secondario e non deve ridurre la leggibilità dei metadati.

Stato vuoto:

- Titolo: **Nessun mazzo**
- Testo: **Crea un mazzo oppure importane uno da testo o YDKE.**
- Azione primaria: **Crea mazzo**
- Azione secondaria: **Importa**

## Editor del mazzo

### Struttura della schermata

1. Top app bar compatta.
2. Riepilogo formato e legalità, espandibile solo se ci sono problemi.
3. `PrimaryTabRow` fissata sotto la top app bar: **Main · 40**, **Extra · 15**, **Side · 15**. I numeri sono sempre presenti.
4. Lista verticale della sezione attiva.
5. Azione persistente **Aggiungi carte**, preferibilmente FAB o pulsante ancorato sopra la navigation bar.

La tab conserva posizione di scroll per sezione. Non mostrare tre colonne contemporanee su telefono.

### Riga carta

Ogni riga contiene:

- miniatura con ratio corretto; placeholder stabile se offline;
- nome su massimo due righe;
- tipo o metadato utile in una riga;
- stato di legalità con icona, testo breve e limite copie;
- stepper quantità con pulsanti meno, valore e più.

L'intera riga apre il dettaglio; i controlli interni eseguono solo la propria azione. Disabilitare **+** al limite del formato e annunciare il motivo. Rimuovere la carta quando la quantità scende a zero, con `Snackbar` **Annulla**.

### Gesture e alternative visibili

- **Tap riga**: dettaglio carta.
- **Tap − / +**: modifica di una copia.
- **Swipe da destra verso sinistra**: rivela **Rimuovi**, senza eliminazione al primo movimento. L'azione resta disponibile anche nell'overflow della riga.
- **Pressione prolungata + trascinamento**: riordino nella stessa sezione. Mostrare handle, feedback aptico leggero e auto-scroll. Offrire anche **Sposta su/giù** nel menu per TalkBack e switch access.
- **Sposta sezione**: azione esplicita nell'overflow apre una `ModalBottomSheet` con sole destinazioni valide. Non affidarsi al drag tra tab.
- **Undo**: ogni rimozione o spostamento distruttivo mostra uno `Snackbar` con il nome della carta.

Non assegnare azioni essenziali a double tap, pinch o swipe senza equivalente visibile. Il drag non deve iniziare durante lo scroll normale.

### Legalità

- Il formato corrente è visibile nell'editor e modificabile dal riepilogo.
- Stato per carta: **Valida**, **Limitata 1**, **Semi-limitata 2**, **Vietata**, **Non disponibile nel formato**.
- Usare icona + testo + colore. Il solo colore non è sufficiente.
- Il riepilogo indica il numero di problemi e porta alla prima carta interessata.
- Al cambio formato ricalcolare in-place, senza perdere scroll o modifiche. Spiegare che il mazzo non viene modificato automaticamente.
- Se il limite è superato, mantenere le copie esistenti ma bloccare ulteriori aggiunte; offrire **Porta al limite** come azione esplicita.

## Ricerca carte

### Flusso

- Campo `SearchBar` in alto, autofocus solo quando l'utente entra tramite **Aggiungi carte**.
- Avviare la ricerca locale mentre si digita, con debounce breve (circa 150–250 ms); non mostrare spinner per query locali rapide.
- Prima della query mostrare ricerche recenti e, se si proviene dall'editor, il mazzo attivo.
- Risultati in lista: art piccola, nome, tipo/attributo, limite copie e azione **+**.
- Il quick-add usa la sezione risolta dalle regole del tipo carta. Se esiste un'ambiguità reale, aprire una bottom sheet; non interrompere ogni aggiunta con un dialog.
- Dopo quick-add, trasformare brevemente **+** in quantità corrente o conferma testuale. Il tap ripetuto non deve duplicare richieste pendenti.
- Tap sulla riga apre il dettaglio; il Back torna agli stessi risultati e alla stessa posizione.

### Stati di ricerca

- Query vuota: cronologia e suggerimento **Cerca per nome carta**.
- Nessun risultato: ripetere la query, proporre correzione e azione **Azzera filtri** se ci sono filtri.
- Catalogo seed incompleto: distinguere **nessun risultato locale** da **catalogo non sincronizzato**.
- Offline: banner non bloccante **Offline: risultati dal catalogo locale**. Le immagini possono mostrare placeholder.
- Errore remoto con risultati locali: mantenere i risultati e mostrare un banner con **Riprova**; non sostituire dati utili con una schermata di errore.

## Import ed export

- L'import parte da Mazzi o dall'overflow dell'editor.
- Usare una schermata dedicata con selettore **Testo / YDKE**, area di input e preview delle tre sezioni.
- Gli errori di parsing devono indicare riga, contenuto non risolto e azione possibile. Usare le stringhe localizzate associate agli `errorKey`, mai chiavi tecniche nude.
- Prima di creare il mazzo mostrare quante carte sono state risolte e quali richiedono intervento. Non scartare righe in silenzio.
- Export usa l'Android Sharesheet e offre **Copia**. Confermare l'avvenuta copia con snackbar, non con dialog.

## Loading, empty ed error states

Applicare questa priorità:

1. errore bloccante senza dati;
2. skeleton solo al primo caricamento senza dati;
3. dati esistenti con indicatore discreto di refresh;
4. empty state contestuale.

Linee guida:

- Skeleton con forma stabile per liste e dettaglio; progress indicator inline per singole azioni.
- Conservare dati già caricati durante refresh e sincronizzazione.
- Banner per problemi recuperabili che lasciano la schermata usabile.
- Snackbar per esito di azioni e undo.
- Full-screen state solo quando il task non può continuare, con titolo, spiegazione semplice, **Riprova** e un'alternativa quando esiste.
- Dialog solo per decisioni distruttive o irreversibili. Errori ordinari non sono dialog.
- Ogni messaggio traduce un `errorKey` in testo orientato all'azione. Log e diagnostica possono conservare la chiave separatamente.

## Accessibilità

- Area touch minima **48 × 48 dp**; non disabilitare l'enforcement Material 3. Distanziare target adiacenti per evitare sovrapposizioni.
- Supportare font scale almeno al 200% senza testo tagliato, controlli sovrapposti o azioni irraggiungibili.
- Ordine TalkBack: nome carta, metadati, quantità, legalità, azioni. Unire i contenuti decorativi in una singola semantica di riga.
- Miniatura con `contentDescription = null` quando il nome è già annunciato. Icone azione hanno descrizioni localizzate e specifiche, per esempio **Aggiungi una copia di Book of Moon**.
- Stepper: pulsanti etichettati, valore annunciato e stato disabilitato con motivo.
- Annunciare errori, completamento import e cambi di quantità tramite live region solo quando serve; evitare annunci durante ogni frame di drag.
- Stato, formato e legalità non dipendono dal colore. Garantire contrasto minimo 4.5:1 per testo normale e 3:1 per testo grande e componenti.
- Rispettare impostazioni di contrasto, tema, font e durata animazioni. Con animazioni ridotte, rimuovere spostamenti decorativi e mantenere feedback istantanei.
- Navigazione completa con tastiera, switch access e D-pad: focus visibile, ordine logico, nessun focus intrappolato.
- Aptica e suono sono rinforzi, mai l'unico feedback.
- Stringhe italiane e inglesi usano risorse con pluralizzazione; non concatenare quantità, nomi e frasi.

## Edge-to-edge e comportamento phone

- Disegnare edge-to-edge, ma applicare `WindowInsets` a top app bar, liste, FAB, bottom sheet e navigation bar.
- La tastiera non deve coprire risultati, input o CTA; mantenere visibile la riga selezionata.
- Contenuto primario entro una colonna leggibile. Su landscape compatto privilegiare altezza utile e top bar ridotta.
- Testare almeno: 360 × 640 dp, 412 × 915 dp, landscape compatto, font 200%, dark mode, gesture navigation e three-button navigation.

## Cosa non copiare dal web

- Layout desktop a tre colonne, rail laterali sticky e pannelli simultanei.
- Hover, tooltip come unica spiegazione, preview che dipendono dal mouse e scaling al passaggio.
- Griglie dense di artwork come editor principale; su telefono servono righe leggibili e controlli espliciti.
- Drag-and-drop come unico modo per spostare o rimuovere carte.
- Modali centrate stile desktop per azioni frequenti; usare schermate, bottom sheet o snackbar Android.
- Breakpoint CSS, container `max-width`, navbar web e hamburger menu.
- Background animati, blur, glow, gradienti e ombre teatrali del campo `duel`; consumano batteria e competono con l'artwork.
- Testo da 10–11 px, target inferiori a 48 dp e controlli compressi.
- Nomi DaisyUI (`base-100`, `btn`, `badge`) nei componenti Android. Mappare il brand ai ruoli semantici Material 3.
- Copia letterale della gerarchia web. Conservare contratti e identità, non la composizione della pagina.
