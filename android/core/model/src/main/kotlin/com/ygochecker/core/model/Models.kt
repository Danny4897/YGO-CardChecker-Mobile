package com.ygochecker.core.model

data class Card(
    val id: Int,
    val name: String,
    val type: String,
    val race: String? = null,
    val attribute: String? = null,
    val attack: Int? = null,
    val defense: Int? = null,
    val level: Int? = null,
    val description: String = "",
    /** First TCG (or OCG) print year when known from catalog sync. */
    val year: Int? = null,
    val imageUrl: String = "https://images.ygoprodeck.com/images/cards_small/%d.jpg".format(id),
    /** Cardmarket lowest price in EUR, from YGOPRODeck's `card_prices`. Null until fetched online. */
    val priceEur: Double? = null,
)

enum class DeckSection { MAIN, EXTRA, SIDE }

/** Same Extra Deck type rule as web `isExtraDeckType` (ydke.service.ts). */
private val EXTRA_DECK_TYPE = Regex("Fusion|Synchro|Synchron|Xyz|XYZ|Link", RegexOption.IGNORE_CASE)

fun isExtraDeckType(type: String): Boolean = EXTRA_DECK_TYPE.containsMatchIn(type)

fun isSpellCard(type: String): Boolean = type.contains("Spell", ignoreCase = true)

fun isTrapCard(type: String): Boolean = type.contains("Trap", ignoreCase = true)

/** Main/Side type band: Monster → Spell → Trap (then passcode). */
fun mainSideTypeRank(type: String): Int = when {
    type.contains("Monster", ignoreCase = true) -> 0
    isSpellCard(type) -> 1
    isTrapCard(type) -> 2
    else -> 3
}

/**
 * Stable deck order: section (Main → Extra → Side), then within Main/Side
 * Monster/Spell/Trap, then unique passcode. Extra is passcode-only.
 */
fun sortDeckCards(cards: List<DeckCard>): List<DeckCard> =
    cards.sortedWith(
        compareBy<DeckCard> { it.section.ordinal }
            .thenBy {
                when (it.section) {
                    DeckSection.EXTRA -> 0
                    DeckSection.MAIN, DeckSection.SIDE -> mainSideTypeRank(it.card.type)
                }
            }
            .thenBy { it.card.id },
    )

/** Same section resolution as web `resolveImportedSection` (deck-text.service.ts). */
fun resolveImportedSection(declared: DeckSection, cardType: String): DeckSection {
    if (declared == DeckSection.SIDE) return DeckSection.SIDE
    if (isExtraDeckType(cardType)) return DeckSection.EXTRA
    if (declared == DeckSection.EXTRA) return DeckSection.MAIN
    return DeckSection.MAIN
}

enum class GameFormat(val id: String) {
    GOAT("goat"),
    EDISON("edison"),
    HAT("hat"),
    TENGU("tengu"),
    TCG("tcg"),
}
enum class AppLanguage(val id: String) { ITALIAN("it"), ENGLISH("en") }

/** Filters for search / browse in the active format. */
data class SearchFilters(
    val typeNeedle: String = "",
    /** Monster/Spell/Trap race (Warrior, Continuous, …). */
    val race: String = "",
    val attribute: String = "",
    val levelMin: Int? = null,
    val levelMax: Int? = null,
    val atkMin: Int? = null,
    val atkMax: Int? = null,
    val defMin: Int? = null,
    val defMax: Int? = null,
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    /**
     * Effect mechanic from scripts / related knowledge
     * (e.g. `gy_effect`, `ss_from_gy`, `searches_deck`) — not starter/extender roles.
     */
    val effectTag: String = "",
    val playableOnly: Boolean = true,
) {
    val isDefault: Boolean
        get() = !hasFacets && playableOnly
    val hasFacets: Boolean
        get() = typeNeedle.isNotEmpty() || race.isNotEmpty() || attribute.isNotEmpty() ||
            levelMin != null || levelMax != null ||
            atkMin != null || atkMax != null ||
            defMin != null || defMax != null ||
            yearMin != null || yearMax != null ||
            effectTag.isNotEmpty()
}

data class DeckCard(val card: Card, val quantity: Int, val section: DeckSection)
data class Decklist(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val cards: List<DeckCard> = emptyList(),
    /** Up to two user-picked presentation card ids (cover art). */
    val coverCardIds: List<Int> = emptyList(),
    /** Visible on profile when true; private decks stay device-only. */
    val isPublic: Boolean = false,
    /** When true this list can be selected as the Puzzle opponent. */
    val isPuzzleOpponent: Boolean = false,
)

enum class LinkedAccountProvider { DISCORD, GOOGLE, KONAMI }

data class UserProfile(
    val username: String = "",
    val avatarCardId: Int? = null,
    val linkedAccounts: Map<LinkedAccountProvider, String> = emptyMap(),
)

data class FriendEntry(
    val id: Long,
    val displayName: String,
    val note: String = "",
)

enum class FriendshipStatus {
    NONE,
    PENDING_OUT,
    PENDING_IN,
    FRIENDS,
}

data class SocialUser(
    val id: String,
    val username: String,
    val friendCode: String,
    val avatarCardId: Int? = null,
    val friendship: FriendshipStatus = FriendshipStatus.NONE,
)

data class SocialPublicDeckSummary(
    val id: String,
    val ownerId: String,
    val ownerUsername: String = "",
    val localDeckId: Long,
    val name: String,
    val coverCardIds: List<Int> = emptyList(),
    val updatedAt: Long = 0L,
)

data class SocialDeckCard(
    val cardId: Int,
    val quantity: Int,
    val section: String,
    val name: String = "",
)

data class SocialPublicDeck(
    val id: String,
    val ownerId: String,
    val ownerUsername: String,
    val localDeckId: Long,
    val name: String,
    val coverCardIds: List<Int> = emptyList(),
    val cards: List<SocialDeckCard> = emptyList(),
    val updatedAt: Long = 0L,
)

data class SocialChatMessage(
    val id: Long,
    val userId: String,
    val username: String,
    val body: String,
    val lang: String = "en",
    val createdAt: Long,
)

data class SocialDmMessage(
    val id: Long,
    val fromUserId: String,
    val toUserId: String,
    val body: String,
    val createdAt: Long,
)

data class CardCollection(
    val id: Long,
    val name: String,
    val cardIds: List<Int> = emptyList(),
)

/** Supabase Auth session state backing [SocialRepository]. */
enum class SocialAuthState { SIGNED_OUT, ANONYMOUS, EMAIL_LINKED }

/** A friend's shared card collection (cloud id — distinct from the local Room [CardCollection.id]). */
data class SocialCollection(
    val id: String,
    val name: String,
    val cardIds: List<Int> = emptyList(),
)

enum class SocialAlertKind { BANLIST_CHANGE, FRIEND_REQUEST, DM, DECK_MESSAGE }

/** Realtime notification row (`user_alerts`) — friend requests, DMs, deck chat, banlist changes. */
data class SocialAlert(
    val id: Long,
    val kind: SocialAlertKind,
    val payload: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val read: Boolean = false,
)

/** Local duel replay notes for future AI deck training. */
data class ReplayEntry(
    val id: Long,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Cover arts for deck tiles: explicit covers, else Extra then Main (web-style). */
fun deckPresentationCards(deck: Decklist, limit: Int = 2): List<DeckCard> {
    val byId = deck.cards.associateBy { it.card.id }
    val picked = deck.coverCardIds.mapNotNull(byId::get).distinctBy { it.card.id }.take(limit)
    if (picked.isNotEmpty()) return picked
    val extra = deck.cards.filter { it.section == DeckSection.EXTRA }
    val main = deck.cards.filter { it.section == DeckSection.MAIN }
    return (extra + main).distinctBy { it.card.id }.take(limit)
}

data class CardLegality(val cardId: Int, val maxCopies: Int, val currentCopies: Int) {
    val isLegal: Boolean get() = currentCopies <= maxCopies
}
data class DeckLegality(val cards: List<CardLegality>) {
    val isLegal: Boolean get() = cards.all(CardLegality::isLegal)
}

data class CatalogSyncProgress(
    val phase: SyncPhase,
    val done: Int,
    val total: Int,
)

enum class SyncPhase { CATALOG, LEGALITY, EFFECT_SCRIPTS, RELATED }

data class OfflinePackStatus(
    val catalogCount: Int,
    val legalityReady: Boolean,
    val effectScriptCount: Int,
    val lastCatalogSyncAt: Long?,
    val lastScriptsSyncAt: Long?,
)

data class EffectScriptSummary(
    val cardId: Int,
    val name: String,
    val roles: List<String>,
    /** Mechanic tags (GY, SS from GY, search, …). */
    val tags: List<String> = emptyList(),
)

/** Synergy / related card from offline related.json pack. */
data class RelatedCardRef(
    val id: Int,
    val name: String,
    val relation: String,
    val score: Double,
    /** Full card art (not cropped small thumbnail). */
    val imageUrl: String = "https://images.ygoprodeck.com/images/cards/%d.jpg".format(id),
)

/** Free-form scouting notes kept per deck for tournament prep. */
data class DeckNotes(
    val deckId: Long,
    val strengths: String = "",
    val weaknesses: String = "",
    val strategy: String = "",
    val updatedAt: Long = 0,
)

enum class MatchResult { WIN, LOSS, DRAW }

/** One round/match logged against a deck — best-of-3 game results plus what got sided. */
data class TournamentMatch(
    val id: Long = 0,
    val deckId: Long,
    val roundLabel: String,
    val opponent: String,
    val result: MatchResult,
    /** Per-game results in play order, e.g. [WIN, LOSS, WIN]. */
    val games: List<MatchResult> = emptyList(),
    /** Human-readable summary of side-deck swaps made between games (built from a card picker). */
    val sideNotes: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
)
