package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult
import com.ygochecker.core.common.valueOrNull
import com.ygochecker.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface CardRepository {
    fun search(query: String): Flow<List<Card>>
    fun search(
        query: String,
        format: GameFormat,
        filters: SearchFilters,
        limit: Int = 50,
    ): Flow<List<Card>>
    /** Playable in [format] (maxCopies > 0), newest passcode first. */
    fun browsePlayable(format: GameFormat, limit: Int = 40): Flow<List<Card>>
    fun randomPlayable(format: GameFormat): Flow<Card?>
    suspend fun get(id: Int): Card?
    /** Local-only batch lookup (no network) — for enriching stored id/name refs (e.g. deck cards). */
    suspend fun getByIds(ids: Collection<Int>): List<Card>
    suspend fun all(): List<Card>
    suspend fun count(): Int
    suspend fun ensureCatalogReady()
    suspend fun resolveByIds(ids: Collection<Int>): AppResult<List<Card>>
    suspend fun resolveByName(name: String): Card?
    /**
     * Name + effect text for [language]. Offline Room catalog is EN-only after sync;
     * this hits YGOPRODeck with the language param when online, else falls back to local.
     */
    suspend fun localized(id: Int, language: AppLanguage): Card?
}

interface DeckRepository {
    fun observeDecks(): Flow<List<Decklist>>
    fun observeDeck(id: Long): Flow<Decklist?>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun setCard(id: Long, card: Card, quantity: Int, section: DeckSection)
    suspend fun setCoverCards(id: Long, coverCardIds: List<Int>)
    suspend fun setPublic(id: Long, isPublic: Boolean)
    suspend fun persistImported(
        name: String,
        cards: List<DeckCard>,
        isExternal: Boolean = false,
        groupName: String? = null,
    ): AppResult<Long>
    suspend fun importText(text: String): AppResult<List<DeckCard>>
    fun exportText(cards: List<DeckCard>): String
    suspend fun importYdke(uri: String): AppResult<List<DeckCard>>
    fun exportYdke(cards: List<DeckCard>): String
    suspend fun importYdk(text: String): AppResult<List<DeckCard>>
    fun exportYdk(cards: List<DeckCard>): String
}

/** Per-deck tournament prep: scouting notes + a match/round log with side-deck tracking. */
interface TournamentRepository {
    fun observeNotes(deckId: Long): Flow<DeckNotes?>
    suspend fun saveNotes(notes: DeckNotes)
    fun observeMatches(deckId: Long): Flow<List<TournamentMatch>>
    suspend fun addMatch(match: TournamentMatch): Long
    suspend fun deleteMatch(id: Long)
}

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile>
    suspend fun setUsername(username: String)
    suspend fun setAvatarCardId(cardId: Int?)
    /** Stores opaque external IDs only — never passwords/tokens in plaintext logs. */
    suspend fun linkAccount(provider: LinkedAccountProvider, externalId: String): AppResult<Unit>
    suspend fun unlinkAccount(provider: LinkedAccountProvider)
    fun observeFriends(): Flow<List<FriendEntry>>
    suspend fun addFriend(displayName: String, note: String = ""): AppResult<Unit>
    suspend fun removeFriend(id: Long)
    fun observeCollections(): Flow<List<CardCollection>>
    suspend fun createCollection(name: String): Long
    suspend fun deleteCollection(id: Long)
    suspend fun addCardToCollection(collectionId: Long, cardId: Int)
    suspend fun removeCardFromCollection(collectionId: Long, cardId: Int)
    fun observeReplays(): Flow<List<ReplayEntry>>
    suspend fun addReplay(title: String, note: String = ""): AppResult<Unit>
    suspend fun removeReplay(id: Long)
}

/**
 * Social backend (Supabase: Postgres + Auth + Realtime).
 * [observeApiUrl] now reports whether the backend is reachable/configured at all —
 * with a fixed hosted project this is effectively always true, kept only so callers
 * written against "is social available" keep working unchanged.
 */
interface SocialRepository {
    fun observeApiUrl(): Flow<String>
    suspend fun ensureSession(username: String, avatarCardId: Int?): AppResult<SocialUser>
    suspend fun refreshMe(): AppResult<SocialUser>
    suspend fun updateMe(username: String, avatarCardId: Int?): AppResult<SocialUser>
    suspend fun searchUsers(query: String): AppResult<List<SocialUser>>
    suspend fun getUser(idOrCode: String): AppResult<SocialUser>
    suspend fun requestFriend(toUserId: String): AppResult<FriendshipStatus>
    suspend fun acceptFriend(fromUserId: String): AppResult<FriendshipStatus>
    suspend fun listFriends(): AppResult<Pair<List<SocialUser>, List<SocialUser>>>
    suspend fun publishDeck(deck: Decklist): AppResult<String>
    suspend fun unpublishDeck(localDeckId: Long): AppResult<Unit>
    suspend fun listPublicDecks(userId: String): AppResult<List<SocialPublicDeckSummary>>
    /** All public decks from every user (browse / chat without friendship). */
    suspend fun listAllPublicDecks(limit: Int = 50): AppResult<List<SocialPublicDeckSummary>>
    suspend fun getPublicDeck(deckId: String): AppResult<SocialPublicDeck>
    suspend fun listDeckMessages(deckId: String, lang: String?): AppResult<List<SocialChatMessage>>
    suspend fun postDeckMessage(deckId: String, body: String, lang: String): AppResult<SocialChatMessage>
    suspend fun listDm(peerUserId: String): AppResult<List<SocialDmMessage>>
    suspend fun postDm(peerUserId: String, body: String): AppResult<SocialDmMessage>

    /** SIGNED_OUT until [ensureSession] runs at least once this process. */
    fun observeAuthState(): Flow<SocialAuthState>

    /** Upgrades the anonymous session to a recoverable one: emails a magic sign-in link. */
    suspend fun sendMagicLink(email: String): AppResult<Unit>

    /** Unread-first, newest-first; updates live while collected (Realtime). */
    fun observeAlerts(): Flow<List<SocialAlert>>
    suspend fun markAlertsRead(ids: List<Long>)

    /** Cards a friend has chosen to share (RLS: only visible between accepted friends). */
    fun observeFriendCollections(peerUserId: String): Flow<List<SocialCollection>>
}

interface PreferenceRepository {
    val format: Flow<GameFormat>
    val language: Flow<AppLanguage>
    suspend fun setFormat(value: GameFormat)
    suspend fun setLanguage(value: AppLanguage)
}

interface OfflinePackRepository {
    fun status(): Flow<OfflinePackStatus>
    fun progress(): Flow<CatalogSyncProgress?>
    /** Clears a stuck/finished progress bar so Settings sync buttons unlock. */
    fun clearProgress()
    /** Fast: legality + HAT catalog + effect scripts. Does not load related/synergies. */
    suspend fun ensureBundledKnowledge()
    /** Background / on-demand: related.json + manual synergies. */
    suspend fun ensureRelatedKnowledge()
    suspend fun syncCatalog(): AppResult<Int>
    suspend fun syncFormatLegality(): AppResult<Int>
    suspend fun syncEffectScripts(fullRemote: Boolean = true): AppResult<Int>
    suspend fun syncRelated(): AppResult<Int>
    /** One-shot: legality + catalog + scripts (+ text enrich) + related/synergies. */
    suspend fun syncAllKnowledge(): AppResult<Int>
    /** Merge printed-text tags/roles into effect_scripts (LUA/AST boost). */
    suspend fun enrichScriptsFromCardText(): AppResult<Int>
    fun maxCopiesOrNull(cardId: Int, format: GameFormat): Int?
    suspend fun effectScriptCount(): Int
    suspend fun effectScript(cardId: Int): EffectScriptSummary?
    suspend fun effectScripts(ids: Collection<Int>): List<EffectScriptSummary>
    suspend fun segocProfile(cardId: Int): SegocProfileSummary?
    suspend fun segocProfiles(ids: Collection<Int>): List<SegocProfileSummary>
    suspend fun relatedCards(cardId: Int, limit: Int = 24): List<RelatedCardRef>
}

fun interface SearchCards {
    fun invoke(query: String, format: GameFormat, filters: SearchFilters): Flow<List<Card>>
}
class DefaultSearchCards @Inject constructor(private val repo: CardRepository) : SearchCards {
    override fun invoke(query: String, format: GameFormat, filters: SearchFilters) =
        repo.search(query.trim(), format, filters)
}

interface BrowsePlayableCards {
    fun invoke(format: GameFormat, limit: Int = 40): Flow<List<Card>>
}
class DefaultBrowsePlayableCards @Inject constructor(private val repo: CardRepository) : BrowsePlayableCards {
    override fun invoke(format: GameFormat, limit: Int) = repo.browsePlayable(format, limit)
}

fun interface RandomPlayableCard {
    fun invoke(format: GameFormat): Flow<Card?>
}
class DefaultRandomPlayableCard @Inject constructor(private val repo: CardRepository) : RandomPlayableCard {
    override fun invoke(format: GameFormat) = repo.randomPlayable(format)
}
fun interface ListDecklists { fun invoke(): Flow<List<Decklist>> }
class DefaultListDecklists @Inject constructor(private val repo: DeckRepository) : ListDecklists {
    override fun invoke() = repo.observeDecks()
}
fun interface GetDecklist { fun invoke(id: Long): Flow<Decklist?> }
class DefaultGetDecklist @Inject constructor(private val repo: DeckRepository) : GetDecklist {
    override fun invoke(id: Long) = repo.observeDeck(id)
}
fun interface CreateDecklist { suspend fun invoke(name: String): Long }
class DefaultCreateDecklist @Inject constructor(private val repo: DeckRepository) : CreateDecklist {
    override suspend fun invoke(name: String) = repo.create(name.ifBlank { "New deck" })
}
fun interface RenameDecklist { suspend fun invoke(id: Long, name: String) }
class DefaultRenameDecklist @Inject constructor(private val repo: DeckRepository) : RenameDecklist {
    override suspend fun invoke(id: Long, name: String) = repo.rename(id, name.trim())
}
fun interface DeleteDecklist { suspend fun invoke(id: Long) }
class DefaultDeleteDecklist @Inject constructor(private val repo: DeckRepository) : DeleteDecklist {
    override suspend fun invoke(id: Long) = repo.delete(id)
}
fun interface SetCardQuantity { suspend fun invoke(id: Long, card: Card, quantity: Int, section: DeckSection) }
class DefaultSetCardQuantity @Inject constructor(
    private val repo: DeckRepository,
    private val legality: EvaluateDeckLegality,
    private val formats: FormatPreference,
) : SetCardQuantity {
    override suspend fun invoke(id: Long, card: Card, quantity: Int, section: DeckSection) {
        val format = formats.values.first()
        val max = legality.maxCopies(card.id, format)
        repo.setCard(id, card, quantity.coerceIn(0, max.coerceAtLeast(0)), section)
    }
}

fun interface ImportDeckFromText { suspend fun invoke(text: String): AppResult<List<DeckCard>> }
class DefaultImportDeckFromText @Inject constructor(private val repo: DeckRepository) : ImportDeckFromText {
    override suspend fun invoke(text: String) = repo.importText(text)
}
fun interface PersistImportedDeck {
    suspend fun invoke(name: String, cards: List<DeckCard>, isExternal: Boolean, groupName: String?): AppResult<Long>
}
class DefaultPersistImportedDeck @Inject constructor(private val repo: DeckRepository) : PersistImportedDeck {
    override suspend fun invoke(name: String, cards: List<DeckCard>, isExternal: Boolean, groupName: String?) =
        repo.persistImported(name.trim().ifBlank { "Imported deck" }, cards, isExternal, groupName?.trim()?.ifBlank { null })
}
fun interface ExportDeckToText { fun invoke(cards: List<DeckCard>): String }
class DefaultExportDeckToText @Inject constructor(private val repo: DeckRepository) : ExportDeckToText {
    override fun invoke(cards: List<DeckCard>) = repo.exportText(cards)
}
fun interface ImportDeckFromYdke { suspend fun invoke(uri: String): AppResult<List<DeckCard>> }
class DefaultImportDeckFromYdke @Inject constructor(private val repo: DeckRepository) : ImportDeckFromYdke {
    override suspend fun invoke(uri: String) = repo.importYdke(uri)
}
fun interface ExportDeckToYdke { fun invoke(cards: List<DeckCard>): String }
class DefaultExportDeckToYdke @Inject constructor(private val repo: DeckRepository) : ExportDeckToYdke {
    override fun invoke(cards: List<DeckCard>) = repo.exportYdke(cards)
}
fun interface ImportDeckFromYdk { suspend fun invoke(text: String): AppResult<List<DeckCard>> }
class DefaultImportDeckFromYdk @Inject constructor(private val repo: DeckRepository) : ImportDeckFromYdk {
    override suspend fun invoke(text: String) = repo.importYdk(text)
}
fun interface ExportDeckToYdk { fun invoke(cards: List<DeckCard>): String }
class DefaultExportDeckToYdk @Inject constructor(private val repo: DeckRepository) : ExportDeckToYdk {
    override fun invoke(cards: List<DeckCard>) = repo.exportYdk(cards)
}
interface FormatPreference {
    val values: Flow<GameFormat>
    suspend fun set(value: GameFormat)
}
class DefaultFormatPreference @Inject constructor(private val repo: PreferenceRepository) : FormatPreference {
    override val values = repo.format
    override suspend fun set(value: GameFormat) = repo.setFormat(value)
}
interface LanguagePreference {
    val values: Flow<AppLanguage>
    suspend fun set(value: AppLanguage)
}
class DefaultLanguagePreference @Inject constructor(private val repo: PreferenceRepository) : LanguagePreference {
    override val values = repo.language
    override suspend fun set(value: AppLanguage) = repo.setLanguage(value)
}

interface EvaluateDeckLegality {
    fun maxCopies(cardId: Int, format: GameFormat): Int
    fun invoke(deck: Decklist, format: GameFormat): DeckLegality
}

class DefaultEvaluateDeckLegality @Inject constructor(
    private val pack: OfflinePackRepository,
) : EvaluateDeckLegality {
    override fun maxCopies(cardId: Int, format: GameFormat): Int {
        pack.maxCopiesOrNull(cardId, format)?.let { return it }
        return stubMax(cardId, format)
    }

    override fun invoke(deck: Decklist, format: GameFormat) = DeckLegality(
        deck.cards.groupBy { it.card.id }.map { (cardId, entries) ->
            CardLegality(cardId, maxCopies(cardId, format), entries.sumOf(DeckCard::quantity))
        },
    )

    private fun stubMax(cardId: Int, format: GameFormat): Int = when (format) {
        GameFormat.TCG -> when (cardId) {
            55144522 -> 0
            83764718, 12580477 -> 1
            else -> 3
        }
        GameFormat.GOAT -> when (cardId) {
            55144522, 83764718 -> 1
            else -> 3
        }
        GameFormat.EDISON, GameFormat.HAT, GameFormat.TENGU -> when (cardId) {
            55144522 -> 0
            83764718, 12580477 -> 1
            else -> 3
        }
    }
}

interface SyncCatalog { suspend fun invoke(): AppResult<Int> }
class DefaultSyncCatalog @Inject constructor(private val pack: OfflinePackRepository) : SyncCatalog {
    override suspend fun invoke() = pack.syncCatalog()
}
interface SyncEffectScripts { suspend fun invoke(fullRemote: Boolean): AppResult<Int> }
class DefaultSyncEffectScripts @Inject constructor(private val pack: OfflinePackRepository) : SyncEffectScripts {
    override suspend fun invoke(fullRemote: Boolean) = pack.syncEffectScripts(fullRemote)
}
interface SyncRelated { suspend fun invoke(): AppResult<Int> }
class DefaultSyncRelated @Inject constructor(private val pack: OfflinePackRepository) : SyncRelated {
    override suspend fun invoke() = pack.syncRelated()
}
interface SyncFormatLegality { suspend fun invoke(): AppResult<Int> }
class DefaultSyncFormatLegality @Inject constructor(private val pack: OfflinePackRepository) : SyncFormatLegality {
    override suspend fun invoke() = pack.syncFormatLegality()
}
/** Catalog + legality + LUA/scripts (text-enriched) + related/synergies. */
interface SyncAllKnowledge { suspend fun invoke(): AppResult<Int> }
class DefaultSyncAllKnowledge @Inject constructor(private val pack: OfflinePackRepository) : SyncAllKnowledge {
    override suspend fun invoke() = pack.syncAllKnowledge()
}
interface ObserveOfflinePack {
    fun status(): Flow<OfflinePackStatus>
    fun progress(): Flow<CatalogSyncProgress?>
    fun clearProgress()
}
class DefaultObserveOfflinePack @Inject constructor(private val pack: OfflinePackRepository) : ObserveOfflinePack {
    override fun status() = pack.status()
    override fun progress() = pack.progress()
    override fun clearProgress() = pack.clearProgress()
}

fun interface SetDeckCovers {
    suspend fun invoke(deckId: Long, coverCardIds: List<Int>)
}
class DefaultSetDeckCovers @Inject constructor(private val repo: DeckRepository) : SetDeckCovers {
    override suspend fun invoke(deckId: Long, coverCardIds: List<Int>) = repo.setCoverCards(deckId, coverCardIds)
}

fun interface SetDeckPublic {
    suspend fun invoke(deckId: Long, isPublic: Boolean)
}
class DefaultSetDeckPublic @Inject constructor(private val repo: DeckRepository) : SetDeckPublic {
    override suspend fun invoke(deckId: Long, isPublic: Boolean) = repo.setPublic(deckId, isPublic)
}


fun interface GetEffectScript {
    suspend fun invoke(cardId: Int): EffectScriptSummary?
}
class DefaultGetEffectScript @Inject constructor(private val pack: OfflinePackRepository) : GetEffectScript {
    override suspend fun invoke(cardId: Int) = pack.effectScript(cardId)
}

fun interface GetEffectScripts {
    suspend fun invoke(ids: Collection<Int>): List<EffectScriptSummary>
}
class DefaultGetEffectScripts @Inject constructor(private val pack: OfflinePackRepository) : GetEffectScripts {
    override suspend fun invoke(ids: Collection<Int>) = pack.effectScripts(ids)
}

fun interface GetSegocProfile {
    suspend fun invoke(cardId: Int): SegocProfileSummary?
}
class DefaultGetSegocProfile @Inject constructor(private val pack: OfflinePackRepository) : GetSegocProfile {
    override suspend fun invoke(cardId: Int) = pack.segocProfile(cardId)
}

fun interface GetSegocProfiles {
    suspend fun invoke(ids: Collection<Int>): List<SegocProfileSummary>
}
class DefaultGetSegocProfiles @Inject constructor(private val pack: OfflinePackRepository) : GetSegocProfiles {
    override suspend fun invoke(ids: Collection<Int>) = pack.segocProfiles(ids)
}

fun interface GetRelatedCards {
    /** Synergy-related cards that are playable in [format] (maxCopies > 0). */
    suspend fun invoke(cardId: Int, format: GameFormat, limit: Int): List<RelatedCardRef>
}
class DefaultGetRelatedCards @Inject constructor(
    private val pack: OfflinePackRepository,
    private val legality: EvaluateDeckLegality,
) : GetRelatedCards {
    override suspend fun invoke(cardId: Int, format: GameFormat, limit: Int): List<RelatedCardRef> {
        val want = limit.coerceAtLeast(1)
        val pool = pack.relatedCards(cardId, (want * 4).coerceAtLeast(48))
        if (pool.isEmpty()) return emptyList()
        val playable = pool.filter { legality.maxCopies(it.id, format) > 0 }
        // If legality pack is over-strict / incomplete, still show synergy graph.
        val chosen = if (playable.isNotEmpty()) playable else pool
        return chosen.distinctBy { it.id }.take(want)
    }
}

data class BudgetSwapSuggestion(
    val expensive: DeckCard,
    val alternative: Card,
    val savingsEur: Double,
)

/** Cheaper synergy-linked alternatives for the deck's priciest Main/Side cards. */
fun interface SuggestBudgetSwaps {
    suspend fun invoke(cards: List<DeckCard>, format: GameFormat, maxSuggestions: Int): List<BudgetSwapSuggestion>
}
class DefaultSuggestBudgetSwaps @Inject constructor(
    private val cardRepo: CardRepository,
    private val related: GetRelatedCards,
) : SuggestBudgetSwaps {
    override suspend fun invoke(cards: List<DeckCard>, format: GameFormat, maxSuggestions: Int): List<BudgetSwapSuggestion> {
        val inDeckIds = cards.map { it.card.id }.toSet()
        val expensive = cards
            .filter { it.section != DeckSection.EXTRA && (it.card.priceEur ?: 0.0) >= MIN_PRICE_EUR }
            .sortedByDescending { (it.card.priceEur ?: 0.0) * it.quantity }
        val results = mutableListOf<BudgetSwapSuggestion>()
        for (dc in expensive) {
            if (results.size >= maxSuggestions) break
            val price = dc.card.priceEur ?: continue
            val best = related.invoke(dc.card.id, format, RELATED_POOL)
                .filterNot { it.id in inDeckIds }
                .mapNotNull { ref -> cardRepo.get(ref.id) }
                .filter { (it.priceEur ?: Double.MAX_VALUE) < price }
                .minByOrNull { it.priceEur!! }
            if (best != null) results += BudgetSwapSuggestion(dc, best, price - best.priceEur!!)
        }
        return results
    }
    private companion object {
        const val MIN_PRICE_EUR = 1.0
        const val RELATED_POOL = 12
    }
}

/** Curated HAT (and future format) decision trees — TrainMyMat-style Flow. */
interface FlowCatalog {
    suspend fun list(format: GameFormat): List<FlowSummary>
    suspend fun get(flowId: String): FlowGraph?
    suspend fun graphs(format: GameFormat): List<FlowGraph>
    fun rolesFor(cardId: Int, format: GameFormat): List<FormatCardRole>
    fun allRoles(format: GameFormat): List<FormatCardRole>
}

fun interface ListFlows {
    suspend fun invoke(format: GameFormat): List<FlowSummary>
}
class DefaultListFlows @Inject constructor(private val catalog: FlowCatalog) : ListFlows {
    override suspend fun invoke(format: GameFormat) = catalog.list(format)
}

fun interface GetFlow {
    suspend fun invoke(flowId: String): FlowGraph?
}
class DefaultGetFlow @Inject constructor(private val catalog: FlowCatalog) : GetFlow {
    override suspend fun invoke(flowId: String) = catalog.get(flowId)
}

/** Local catalog first, then YGOPRODeck by id (OCR passcode path). */
fun interface ResolveCardById {
    suspend fun invoke(id: Int): Card?
}
class DefaultResolveCardById @Inject constructor(private val repo: CardRepository) : ResolveCardById {
    override suspend fun invoke(id: Int): Card? {
        repo.get(id)?.let { return it }
        return repo.resolveByIds(listOf(id)).valueOrNull?.firstOrNull()
    }
}

/** Localized name/effect for detail sheets (EN catalog + live language fetch). */
fun interface GetLocalizedCard {
    suspend fun invoke(id: Int, language: AppLanguage): Card?
}
class DefaultGetLocalizedCard @Inject constructor(private val repo: CardRepository) : GetLocalizedCard {
    override suspend fun invoke(id: Int, language: AppLanguage) = repo.localized(id, language)
}

/** Exact name resolve, then first search hit (OCR name fallback). */
fun interface ResolveCardByName {
    suspend fun invoke(name: String): Card?
}
class DefaultResolveCardByName @Inject constructor(private val repo: CardRepository) : ResolveCardByName {
    override suspend fun invoke(name: String): Card? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        repo.resolveByName(trimmed)?.let { return it }
        return repo.search(trimmed).first().firstOrNull()
    }
}
