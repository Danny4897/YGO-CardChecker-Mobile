# Design token Android per YGOChecker

Questi token adattano l'identità `duel` a Material 3 e Jetpack Compose. I valori web di partenza sono: fondo indaco `oklch(18–22% 0.03–0.035 265)`, testo avorio `oklch(92% 0.02 85)`, oro primario `oklch(78% 0.14 85)`, blu secondario `oklch(55% 0.12 250)` e corallo terziario `oklch(62% 0.16 35)`.

La mappatura non è una conversione letterale DaisyUI. I colori sono stati regolati per i rapporti foreground/background e per i ruoli Material 3. Prima del rilascio, verificare ogni coppia con test di contrasto automatizzati e screenshot test.

## Strategia del tema

- **Brand scheme predefinito** in light e dark, selezionato dal tema di sistema.
- **Dynamic color disattivato di default**: il colore del wallpaper non deve cambiare legalità, formato o identità del prodotto. Può diventare una preferenza futura, limitata alle superfici e mai ai colori semantici.
- **Dark non significa nero**: usare superfici indaco molto scure derivate da `duel`.
- **Light non significa bianco**: usare superfici avorio leggermente calde, in modo che artwork e oro mantengano separazione.
- Il colore primario indica azione, selezione e focus. Non usarlo come decorazione diffusa.
- Le immagini delle carte sono già cromaticamente dense; l'interfaccia circostante resta contenuta.

## ColorScheme Material 3

I valori sono espressi come RGB esadecimale. In Compose usare `Color(0xFFRRGGBB)`.

### Tema chiaro

| Ruolo Material 3 | Valore | Uso YGOChecker |
|---|---:|---|
| `primary` | `#725B00` | CTA, tab attiva, focus, quantità selezionata |
| `onPrimary` | `#FFF8E1` | Contenuto su primary |
| `primaryContainer` | `#FFE08A` | Selezione attenuata, stato mazzo attivo |
| `onPrimaryContainer` | `#241A00` | Contenuto su primaryContainer |
| `inversePrimary` | `#E8C45C` | Primary su superfici inverse |
| `secondary` | `#2F617D` | Azioni secondarie e informazioni di formato |
| `onSecondary` | `#F7FBFF` | Contenuto su secondary |
| `secondaryContainer` | `#CDE8F8` | Filtri e informazioni selezionate |
| `onSecondaryContainer` | `#0A3448` | Contenuto su secondaryContainer |
| `tertiary` | `#984737` | Accent raro, import/export e attenzione non critica |
| `onTertiary` | `#FFF8F5` | Contenuto su tertiary |
| `tertiaryContainer` | `#FFDAD2` | Contenitore tertiary attenuato |
| `onTertiaryContainer` | `#3D0801` | Contenuto su tertiaryContainer |
| `background` | `#FAF8F2` | Sfondo app |
| `onBackground` | `#1C1B17` | Testo principale |
| `surface` | `#FAF8F2` | Scaffold e superfici base |
| `onSurface` | `#1C1B17` | Testo e icone principali |
| `surfaceVariant` | `#E4E2DC` | Compatibilità con componenti che usano ancora il ruolo variant |
| `onSurfaceVariant` | `#474743` | Metadati e icone secondarie |
| `surfaceTint` | `#725B00` | Elevazione tonale Material |
| `inverseSurface` | `#30302E` | Snackbar e superfici inverse |
| `inverseOnSurface` | `#F3F0E8` | Contenuto su inverseSurface |
| `error` | `#BA1A1A` | Errori e stato vietato |
| `onError` | `#FFF8F6` | Contenuto su error |
| `errorContainer` | `#FFDAD6` | Banner errore |
| `onErrorContainer` | `#410002` | Contenuto su errorContainer |
| `outline` | `#787772` | Bordi ad alto rilievo |
| `outlineVariant` | `#C9C6BF` | Divisori e bordi discreti |
| `scrim` | `#000000` al 45% | Scrim di dialog e bottom sheet |
| `surfaceDim` | `#DBDAD3` | Superficie attenuata |
| `surfaceBright` | `#FAF8F2` | Superficie più luminosa |
| `surfaceContainerLowest` | `#FFFDF8` | Input e card sopra una superficie |
| `surfaceContainerLow` | `#F5F2EB` | Sezioni leggere |
| `surfaceContainer` | `#EFEEE7` | Navigation bar e contenitori standard |
| `surfaceContainerHigh` | `#E9E8E1` | Sheet e menu |
| `surfaceContainerHighest` | `#E3E2DB` | Controlli premuti e skeleton |

### Tema scuro

| Ruolo Material 3 | Valore | Uso YGOChecker |
|---|---:|---|
| `primary` | `#E8C45C` | CTA, tab attiva, focus |
| `onPrimary` | `#3A2F00` | Contenuto su primary |
| `primaryContainer` | `#5A4700` | Selezione attenuata |
| `onPrimaryContainer` | `#FFE08A` | Contenuto su primaryContainer |
| `inversePrimary` | `#725B00` | Primary su superfici chiare inverse |
| `secondary` | `#99CBEA` | Informazioni e azioni secondarie |
| `onSecondary` | `#083548` | Contenuto su secondary |
| `secondaryContainer` | `#1B4A61` | Filtri e informazioni selezionate |
| `onSecondaryContainer` | `#CDE8F8` | Contenuto su secondaryContainer |
| `tertiary` | `#FFB4A6` | Accent raro |
| `onTertiary` | `#5F160D` | Contenuto su tertiary |
| `tertiaryContainer` | `#7B2E20` | Contenitore tertiary attenuato |
| `onTertiaryContainer` | `#FFDAD2` | Contenuto su tertiaryContainer |
| `background` | `#151823` | Sfondo app indaco |
| `onBackground` | `#ECE8DC` | Testo principale avorio |
| `surface` | `#151823` | Scaffold e superfici base |
| `onSurface` | `#ECE8DC` | Testo e icone principali |
| `surfaceVariant` | `#343945` | Compatibilità con componenti variant |
| `onSurfaceVariant` | `#C7C6CC` | Metadati e icone secondarie |
| `surfaceTint` | `#E8C45C` | Elevazione tonale Material |
| `inverseSurface` | `#E8E6DE` | Snackbar chiara opzionale |
| `inverseOnSurface` | `#2E303A` | Contenuto su inverseSurface |
| `error` | `#FFB4AB` | Errori e stato vietato |
| `onError` | `#690005` | Contenuto su error |
| `errorContainer` | `#93000A` | Banner errore |
| `onErrorContainer` | `#FFDAD6` | Contenuto su errorContainer |
| `outline` | `#92919A` | Bordi ad alto rilievo |
| `outlineVariant` | `#454853` | Divisori e bordi discreti |
| `scrim` | `#000000` al 60% | Scrim di dialog e bottom sheet |
| `surfaceDim` | `#151823` | Superficie attenuata |
| `surfaceBright` | `#3A3E49` | Superficie più luminosa |
| `surfaceContainerLowest` | `#10131D` | Livello più basso |
| `surfaceContainerLow` | `#191C27` | Sezioni leggere |
| `surfaceContainer` | `#1E222D` | Navigation bar e contenitori standard |
| `surfaceContainerHigh` | `#292D38` | Sheet e menu |
| `surfaceContainerHighest` | `#343945` | Controlli premuti e skeleton |

### Token semantici estesi

`ColorScheme` copre l'errore, ma non successo, avviso e informazione. Esporre una `DuelExtendedColors` immutabile tramite `CompositionLocal`:

| Token | Light | Dark | Significato |
|---|---:|---:|---|
| `success` | `#356A2C` | `#9AD67D` | Operazione riuscita, carta valida |
| `onSuccess` | `#F7FFF2` | `#11380B` | Contenuto su success |
| `successContainer` | `#B8F2A4` | `#1D511F` | Contenitore successo |
| `onSuccessContainer` | `#0B390A` | `#B8F2A4` | Contenuto su successContainer |
| `warning` | `#8B5000` | `#FFB95C` | Limite copie, catalogo non aggiornato |
| `onWarning` | `#FFF8F2` | `#472A00` | Contenuto su warning |
| `warningContainer` | `#FFDDB8` | `#633B00` | Contenitore avviso |
| `onWarningContainer` | `#2C1600` | `#FFDDB8` | Contenuto su warningContainer |
| `info` | `#2F617D` | `#99CBEA` | Offline, sincronizzazione, formato |
| `onInfo` | `#F7FBFF` | `#083548` | Contenuto su info |
| `infoContainer` | `#CDE8F8` | `#1B4A61` | Contenitore informazione |
| `onInfoContainer` | `#0A3448` | `#CDE8F8` | Contenuto su infoContainer |

I badge di legalità usano:

- valida: icona check + `success`;
- limitata/semi-limitata: testo **1** o **2** + `warning`;
- vietata/non disponibile: icona divieto + `error`;
- sconosciuta o non sincronizzata: icona info + `info`.

Il colore non sostituisce mai testo o icona. Non creare un colore diverso per ogni formato.

## Tipografia

### Famiglie

- **Titoli di brand**: Rajdhani, peso 600. Usarla per nome app, nomi mazzo e titoli principali.
- **UI e contenuto**: Roboto o sans di sistema Android. Usarla per carte, descrizioni, controlli, quantità e messaggi.
- Non usare Rajdhani per body, input, pulsanti, badge o testi inferiori a 18 sp.
- Includere i font come risorse locali e non scaricarli al runtime. Se Rajdhani non è disponibile, ricadere sulla sans di sistema.

Questa separazione conserva l'energia del tema `duel` senza trasformare l'app in una replica del sito.

### Scala Material 3

| Token Compose | Dimensione / line height | Peso | Famiglia | Uso |
|---|---:|---:|---|---|
| `displaySmall` | 36 / 44 sp | 600 | Rajdhani | Solo empty state o onboarding raro |
| `headlineLarge` | 32 / 40 sp | 600 | Rajdhani | Titolo root con spazio sufficiente |
| `headlineMedium` | 28 / 36 sp | 600 | Rajdhani | Empty state e titoli di sezione |
| `headlineSmall` | 24 / 32 sp | 600 | Rajdhani | Titolo dettaglio |
| `titleLarge` | 22 / 28 sp | 600 | Rajdhani | Nome mazzo e top app bar |
| `titleMedium` | 16 / 24 sp | 500 | System | Titolo riga e sheet |
| `titleSmall` | 14 / 20 sp | 500 | System | Sottosezione |
| `bodyLarge` | 16 / 24 sp | 400 | System | Descrizioni e testo carta |
| `bodyMedium` | 14 / 20 sp | 400 | System | Liste e messaggi |
| `bodySmall` | 12 / 16 sp | 400 | System | Metadati secondari |
| `labelLarge` | 14 / 20 sp | 500 | System | Pulsanti e navigation item |
| `labelMedium` | 12 / 16 sp | 500 | System | Chip e badge |
| `labelSmall` | 11 / 16 sp | 500 | System | Conteggi compatti, minimo consentito |

Regole:

- Usare `sp`, rispettare font scale e non fissare l'altezza dei container al testo.
- Numeri di quantità e conteggi usano cifre tabulari se disponibili.
- Massimo due righe per nome carta in lista; testo completo nel dettaglio e nella semantica.
- Descrizione carta con line height almeno 1.5× e larghezza leggibile.
- Nessun testo funzionale sotto 11 sp.

## Spaziatura e dimensioni

Scala base in `dp`:

| Token | Valore | Uso |
|---|---:|---|
| `space0` | 0 | Reset intenzionale |
| `space1` | 4 | Gap interno minimo |
| `space2` | 8 | Icona-label, righe compatte |
| `space3` | 12 | Padding interno riga |
| `space4` | 16 | Margine schermata phone |
| `space5` | 20 | Gruppi di contenuto |
| `space6` | 24 | Sezioni principali |
| `space8` | 32 | Empty state e separazione forte |
| `space10` | 40 | Area introduttiva |
| `space12` | 48 | Touch target minimo |

Regole:

- Margine orizzontale phone: 16 dp; può scendere a 12 dp solo sotto 360 dp.
- Touch target minimo: 48 × 48 dp, anche se l'icona è 20–24 dp.
- Riga carta: altezza guidata dal contenuto, circa 76–88 dp; non fissarla se il font scale cresce.
- Artwork lista: circa 48 × 70 dp, ratio originale preservato.
- Gap tra target adiacenti: almeno 8 dp quando le aree touch non sono chiaramente separate.
- Applicare gli inset di sistema, non codificare padding per status o navigation bar.

## Forme, bordi ed elevazione

### Shape scale

| Token | Radius | Uso |
|---|---:|---|
| `extraSmall` | 4 dp | Indicatori e thumbnail |
| `small` | 8 dp | Chip, badge, controlli compatti |
| `medium` | 12 dp | Search field, righe contenute |
| `large` | 16 dp | Card, banner, sheet interno |
| `extraLarge` | 28 dp | Modal bottom sheet e FAB |
| `full` | 50% | Icon button filled e pill |

- Usare le shape Material 3, senza copiare direttamente i radius CSS DaisyUI.
- Preferire separazione con `surfaceContainer*` e divisori `outlineVariant`.
- Elevazione tonale prima delle ombre. Ombre massime: 1–3 dp per bar e card, 6 dp per sheet e menu.
- Vietati glow oro, blur decorativi, vetro e ring multipli del tema web.

## Regole componenti Compose

### Scaffold e barre

- Una sola gerarchia `Scaffold` per schermata. Propagare `innerPadding` e `WindowInsets` senza duplicarli.
- `NavigationBar` usa `surfaceContainer`; destinazione attiva con `secondaryContainer` o `primaryContainer`, non un blocco primary saturo.
- `TopAppBar` usa `surface` o `surfaceContainerLow`. Titolo `titleLarge`; azioni con `IconButton` da almeno 48 dp e tooltip quando l'icona non è ovvia.
- FAB primary solo per l'azione dominante della root: nuovo mazzo o aggiungi carte.

### Ricerca

- Usare la `SearchBar` Material 3 corrente con stato espanso/collassato; il campo occupa la larghezza utile su phone.
- Risultati come `LazyColumn`, chiavi stabili per passcode e `contentType` coerente.
- Spinner solo se non esiste contenuto e la ricerca supera una soglia percepibile. Durante refresh conservare i risultati.
- Filtri come `FilterChip`; azioni contestuali leggere come `AssistChip`. Evitare file orizzontali di chip non scorribili.

### Editor mazzo

- Sezioni con `PrimaryTabRow`, etichetta e conteggio. Non usare chip per Main/Extra/Side.
- Riga carta come superficie piatta o `ListItem`, non card annidata. Divisore `outlineVariant`.
- Stepper custom composto da due `IconButton` e un valore centrale. L'area totale non deve comprimere i target.
- Stato legalità con `Badge` solo per conteggi molto brevi; per il significato usare chip compatto o testo inline.
- Riordino con handle dedicato, feedback aptico e alternativa nel menu.
- Swipe action con sfondo semantico e conferma visiva; sempre `SnackbarHost` con undo.

### Feedback

- `Snackbar` per successo, copia, rimozione e undo. Una sola snackbar alla volta, messaggio breve e azione testuale.
- Banner inline per offline, sync fallita o catalogo parziale. Non usare snackbar persistenti per stati continui.
- `ModalBottomSheet` per selezione sezione, filtri mobili e azioni contestuali multiple.
- `AlertDialog` solo per eliminazione mazzo, sovrascrittura o perdita dati. Il pulsante distruttivo usa `error`, non primary.
- Skeleton su `surfaceContainerHighest` senza shimmer aggressivo. Se animato, movimento breve e disattivabile.

### Immagini carta

- `ContentScale.Crop` solo nelle miniature con ratio fisso; nel dettaglio usare `Fit`.
- Placeholder usa `surfaceContainerHighest` e un'icona neutra. Nessun gradiente o falso artwork.
- Se il nome è già vicino all'immagine, la miniatura è decorativa per TalkBack.
- Evitare ombre marcate intorno a ogni carta. L'artwork deve restare il punto cromatico principale.

## Stati interattivi

Ogni componente condiviso deve definire:

- default;
- pressed con overlay o container più alto;
- focused con indicatore primary ad alto contrasto;
- selected con container attenuato e semantica selezionata;
- disabled con contenuto e container ridotti, ma ancora leggibili;
- loading senza cambio di dimensione;
- error con messaggio vicino e collegamento semantico.

Non simulare hover su phone. Hover può essere supportato su mouse/stylus senza essere necessario al task.

## Motion e aptica

- 100–150 ms per press e cambio quantità.
- 200–250 ms per sheet, espansione e cambio contenuto.
- Usare curve standard Material con decelerazione in entrata.
- Animare opacità e trasformazioni, non dimensioni di layout complesse nelle liste.
- Nessuna animazione ambientale, particella, griglia in movimento o artwork flottante.
- Rispettare la scala animazioni del sistema. Se è zero o ridotta, sostituire transizioni spaziali con cambi immediati o brevi crossfade.
- Aptica leggera per inizio drag, raggiungimento limite e commit di reorder; mai su ogni incremento normale.

## Implementazione consigliata

Nel modulo `core:designsystem`:

- `DuelTheme`: sceglie light/dark e applica `MaterialTheme`;
- `DuelLightColorScheme` e `DuelDarkColorScheme`;
- `DuelExtendedColors` e relativo `CompositionLocal`;
- `DuelTypography`, `DuelShapes` e `DuelSpacing`;
- componenti condivisi solo quando hanno comportamento stabile, per esempio `DeckCardRow`, `LegalityStatus`, `QuantityStepper`, `InlineBanner` e `EmptyState`.

I feature module consumano ruoli come `MaterialTheme.colorScheme.primary`, non costanti RGB. I colori raw restano privati del design system.

Esempio di confine API:

```kotlin
@Immutable
data class DuelExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)
```

## Checklist di verifica

- Contrasto automatizzato per tutte le coppie `onX` / `X` e token estesi.
- Preview light, dark, font 1.0× e 2.0× per ogni componente condiviso.
- Screenshot test per root, editor, ricerca, empty, offline ed errore.
- Test TalkBack manuale per quick-add, stepper, legalità, reorder e undo.
- Test touch target con Layout Inspector.
- Test edge-to-edge con gesture navigation, three-button navigation e tastiera.
- Test daltonismo: legalità comprensibile in scala di grigi.
- Nessun RGB raw fuori da `core:designsystem`.

## Riferimenti

- Material 3 in Compose: <https://developer.android.com/develop/ui/compose/designsystems/material3>
- Theming Material: <https://developer.android.com/develop/ui/compose/designsystems/material>
- Typography Compose: <https://developer.android.com/develop/ui/compose/text/fonts>
- Accessibilità Compose: <https://developer.android.com/develop/ui/compose/accessibility>
- Layout adattivi: <https://developer.android.com/develop/ui/compose/layouts/adaptive>
