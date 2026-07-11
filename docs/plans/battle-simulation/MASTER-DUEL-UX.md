# Master Duel / MDPro3 — UX/UI Reference

Riferimento per riprodurre fedelmente il duello in YGOChecker. MDPro3 replica gli asset e il layout di Master Duel (Konami) su motore YGOPro.

## Layout schermo duello (vista giocatore)

```
┌─────────────────────────────────────────────────────────────┐
│ [☰ Menu]                              [LP opp] [Mate opp]   │
│                    [dorso carte avversario ×N]              │
├──────────┬──────────────────────────────────────┬───────────┤
│ Field    │  S/T ×5 avversario (back row)        │ GY opp    │
│ Spell    │  MMZ ×5 avversario                   │           │
│ opp      │      [EMZ-L]    [EMZ-R]              │ ED opp    │
│          │  MMZ ×5 giocatore                    │           │
│ Field    │  S/T ×5 giocatore                    │ GY tuo    │
│ Spell    │                                      │ Deck tuo  │
│ tuo      │                                      │           │
├──────────┴──────────────────────────────────────┴───────────┤
│              MANO (ventaglio, bordi sovrapposti)            │
│ [LP tuo]                              [Mate tuo]            │
└─────────────────────────────────────────────────────────────┘
     Rail destra (default): [Fase corrente] [Auto] [i] [Log]
```

### Zone (ordine Master Duel)

| Zona | Posizione | Note UI |
|------|-----------|---------|
| Main Monster Zone ×5 | Centro, fila per giocatore | Carte verticali = ATK, orizzontali = DEF |
| Spell & Trap ×5 | Dietro i mostri (back row) | Set = dorso, badge "S" |
| Extra Monster Zone ×2 | Tra le due file MMZ | Condivise; Link/Synchro da ED |
| Field Zone ×1 | Sinistra, per giocatore | Solo Field Spell |
| Graveyard | Destra | Pila con contatore |
| Deck | Destra sotto GY | Dorso mazzo + contatore |
| Extra Deck | Sinistra sotto Field | Dorso ED + contatore |
| Banish | Accanto GY (MD) | Pila separata |

### HUD

- **LP**: grandi numeri cyan/glow; avversario in alto, tuo in basso (lati opposti).
- **Mano avversario**: solo dorsi, numero carte visibile; mai testo nome.
- **Fasi**: pulsante grande in basso a destra mostra fase corrente; tap = avanza (Main 1 → Battle → Main 2 → End → passa turno).
- **Primo turno**: chi va primo non può attaccare (regola MD).

### Interazioni carta (Master Duel)

1. **Tap carta in mano** → popup dettaglio (arte grande, testo, ATK/DEF/Livello) + azioni contestuali.
2. **Evocazione** → scegli zona MMZ (drag su PC; tap zona su mobile) → scelta ATK/DEF se applicabile.
3. **Attacco** → tap mostro ATK → "Confirm" → tap bersaglio o attacco diretto. Swipe verso avversario = shortcut Battle Phase.
4. **Magie/Trappole** → piazza su S/T zone prima di dichiarare attivazione (zona piena = blocco).
5. **Chain** → overlay centrale con stack LIFO; conferma attivazione effetti.

### Rail destra (bottom-right)

| Icona | Funzione |
|-------|----------|
| Pulsante fase | Avanza fase / End Turn; etichetta = fase corrente |
| Auto / carta | Activation Confirmation (Auto / Manual / On) |
| `i` | Field Status: conteggi deck, hand, GY, banish per entrambi |
| Log | Toggle duel log (overlay laterale) |

### MDPro3 vs Master Duel

- **MDPro3**: stesso layout visivo, asset MD, modalità SOLO/ONLINE/PUZZLE; deck editor YDK/YDKe; nessuna differenza sostanziale sul campo.
- **YGOChecker MVP**: motore semplificato; EMZ visuali; pile Deck/GY/ED con contatori; popup carta; rail fase+log+info.

## Gap attuale → target

| Elemento | Stato pre-refactor | Target |
|----------|-------------------|--------|
| Playmat full-screen | Header app visibile | Duello immersivo |
| Pile Deck/GY/ED | Assenti | Pile laterali con count |
| EMZ centrali | Assenti | 2 slot visivi |
| Mano avversario | Assente | Dorsi ×N |
| Popup carta | Pannello sotto mano | Overlay centrale MD-style |
| Pulsante fase | "Fase successiva" generico | Grande, bottom-right, nome fase |
| Field info `i` | Assente | Modal conteggi |
| Log | Sempre visibile | Toggle rail |

## Fonti

- [Gameplay.tips — Duel Screen Guide](https://gameplay.tips/guides/yu-gi-oh-master-duel-duel-screen-info-guide-duel-field-menu-and-icons.html)
- [Master Duel Meta — Expanded Rule Book](https://www.masterduelmeta.com/articles/guides/expanded-rule-book)
- [Yu-Gi-Oh Wiki — Field](https://yugioh.fandom.com/wiki/Field)
- [MDPro3 GitHub](https://github.com/SethPDA/MDPro3)
