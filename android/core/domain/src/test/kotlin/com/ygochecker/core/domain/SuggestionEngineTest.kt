package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.CatalogSyncProgress
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.EffectScriptSummary
import com.ygochecker.core.model.FlowGraph
import com.ygochecker.core.model.FlowSummary
import com.ygochecker.core.model.FormatCardRole
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.HatCardRole
import com.ygochecker.core.model.OfflinePackStatus
import com.ygochecker.core.model.RelatedCardRef
import com.ygochecker.core.model.SearchFilters
import com.ygochecker.core.model.SegocProfileSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    private val starterId = 1
    private val ashBlossomId = 2
    private val decoyId = 3

    private val starter = Card(starterId, "Starter", "Effect Monster", attack = 1800, defense = 1200)
    private val ashBlossom = Card(ashBlossomId, "Ash Blossom", "Effect Monster", attack = 0, defense = 1800)
    private val decoy = Card(decoyId, "Decoy", "Effect Monster", attack = 1000, defense = 1000)

    private val deck = Decklist(
        id = 1L,
        name = "t",
        updatedAt = 0L,
        cards = listOf(DeckCard(starter, quantity = 1, section = DeckSection.MAIN)),
    )

    private val decks = FakeDecks(mapOf(1L to deck))
    private val emptyCatalog = FakeCatalog(emptyList())

    private val pack = FakePack(
        tags = mapOf(
            starterId to listOf("searches_deck"),
            ashBlossomId to listOf("negates"),
            decoyId to listOf("searches_deck"), // returned by the tag search but does NOT actually have "negates"
        ),
    )

    @Test
    fun `role gaps are computed from derived roles and flag under the floor`() = runBlocking {
        val report = DefaultAnalyzeDeckRoleGaps(decks, pack, emptyCatalog).invoke(1L, GameFormat.HAT)

        assertEquals(1, report.roleCounts[HatCardRole.SEARCHER])
        assertTrue(report.gaps.any { it.role == HatCardRole.INTERRUPT && it.currentCount == 0 && it.recommendedMin == 3 })
        assertTrue(report.gaps.any { it.role == HatCardRole.REMOVAL && it.currentCount == 0 })
        assertTrue(report.gaps.any { it.role == HatCardRole.EXTENDER && it.currentCount == 0 })
        assertFalse(report.gaps.any { it.role == HatCardRole.SEARCHER })
    }

    @Test
    fun `curated role overrides derived role`() = runBlocking {
        val curated = FakeCatalog(listOf(FormatCardRole(starterId, "hat", HatCardRole.INTERRUPT, priority = 5)))
        val report = DefaultAnalyzeDeckRoleGaps(decks, pack, curated).invoke(1L, GameFormat.HAT)
        assertEquals(1, report.roleCounts[HatCardRole.INTERRUPT])
        // Derived SEARCHER role must NOT also apply once a curated entry exists for this card.
        assertEquals(null, report.roleCounts[HatCardRole.SEARCHER])
    }

    @Test
    fun `suggestions fill the biggest gap, rank by synergy, and drop false tag matches`() = runBlocking {
        val cardRepo = FakeCardRepo(
            byTag = mapOf(
                "negates" to listOf(ashBlossom, decoy),
            ),
        )
        val related = FakeRelated(mapOf(starterId to listOf(RelatedCardRef(ashBlossomId, "Ash Blossom", "x", 2.5))))
        val gaps = DefaultAnalyzeDeckRoleGaps(decks, pack, emptyCatalog)
        val useCase = DefaultSuggestSynergisticCards(decks, cardRepo, pack, gaps, related)

        val suggestions = useCase.invoke(1L, GameFormat.HAT, maxSuggestions = 5)

        assertEquals(1, suggestions.size)
        val top = suggestions.first()
        assertEquals(ashBlossomId, top.card.id)
        assertEquals(HatCardRole.INTERRUPT, top.fillsRole)
        assertEquals(2.5, top.synergyScore, 0.0001)
        assertTrue(top.reason.contains("Starter"))
        // The decoy matched the SQL tag search but its real tags don't classify as INTERRUPT.
        assertFalse(suggestions.any { it.card.id == decoyId })
    }
}

private class FakeDecks(private val byId: Map<Long, Decklist>) : DeckRepository by UnsupportedDeckRepository {
    override fun observeDeck(id: Long): Flow<Decklist?> = flowOf(byId[id])
}

private object UnsupportedDeckRepository : DeckRepository {
    override fun observeDecks() = error("unused")
    override fun observeDeck(id: Long) = error("unused")
    override suspend fun create(name: String) = error("unused")
    override suspend fun rename(id: Long, name: String) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
    override suspend fun setCard(id: Long, card: Card, quantity: Int, section: DeckSection) = error("unused")
    override suspend fun setCoverCards(id: Long, coverCardIds: List<Int>) = error("unused")
    override suspend fun setPublic(id: Long, isPublic: Boolean) = error("unused")
    override suspend fun setPuzzleOpponent(id: Long, isPuzzleOpponent: Boolean) = error("unused")
    override suspend fun persistImported(name: String, cards: List<DeckCard>) = error("unused")
    override suspend fun importText(text: String) = error("unused")
    override fun exportText(cards: List<DeckCard>) = error("unused")
    override suspend fun importYdke(uri: String) = error("unused")
    override fun exportYdke(cards: List<DeckCard>) = error("unused")
    override suspend fun importYdk(text: String) = error("unused")
    override fun exportYdk(cards: List<DeckCard>) = error("unused")
}

private class FakeCatalog(private val roles: List<FormatCardRole>) : FlowCatalog {
    override suspend fun list(format: GameFormat): List<FlowSummary> = emptyList()
    override suspend fun get(flowId: String): FlowGraph? = null
    override suspend fun graphs(format: GameFormat): List<FlowGraph> = emptyList()
    override fun rolesFor(cardId: Int, format: GameFormat) = allRoles(format).filter { it.cardId == cardId }
    override fun allRoles(format: GameFormat) = roles.filter { it.formatId == format.id }
}

private class FakePack(private val tags: Map<Int, List<String>>) : OfflinePackRepository {
    override fun status(): Flow<OfflinePackStatus> = emptyFlow()
    override fun progress(): Flow<CatalogSyncProgress?> = emptyFlow()
    override fun clearProgress() = Unit
    override suspend fun ensureBundledKnowledge() = Unit
    override suspend fun ensureRelatedKnowledge() = Unit
    override suspend fun syncCatalog(): AppResult<Int> = error("not used")
    override suspend fun syncFormatLegality(): AppResult<Int> = error("not used")
    override suspend fun syncEffectScripts(fullRemote: Boolean): AppResult<Int> = error("not used")
    override suspend fun syncRelated(): AppResult<Int> = error("not used")
    override suspend fun syncAllKnowledge(): AppResult<Int> = error("not used")
    override suspend fun enrichScriptsFromCardText(): AppResult<Int> = error("not used")
    override fun maxCopiesOrNull(cardId: Int, format: GameFormat): Int? = 3
    override suspend fun effectScriptCount(): Int = 0
    override suspend fun effectScript(cardId: Int): EffectScriptSummary? = null
    override suspend fun effectScripts(ids: Collection<Int>): List<EffectScriptSummary> =
        ids.mapNotNull { id -> tags[id]?.let { EffectScriptSummary(id, "c$id", emptyList(), it) } }
    override suspend fun segocProfile(cardId: Int): SegocProfileSummary? = null
    override suspend fun segocProfiles(ids: Collection<Int>): List<SegocProfileSummary> = emptyList()
    override suspend fun relatedCards(cardId: Int, limit: Int): List<RelatedCardRef> = emptyList()
}

private class FakeCardRepo(private val byTag: Map<String, List<Card>>) : CardRepository {
    override fun search(query: String): Flow<List<Card>> = error("unused")
    override fun search(query: String, format: GameFormat, filters: SearchFilters, limit: Int): Flow<List<Card>> =
        flowOf(byTag[filters.effectTag].orEmpty())
    override fun browsePlayable(format: GameFormat, limit: Int): Flow<List<Card>> = error("unused")
    override fun randomPlayable(format: GameFormat): Flow<Card?> = error("unused")
    override suspend fun get(id: Int): Card? = error("unused")
    override suspend fun getByIds(ids: Collection<Int>): List<Card> = error("unused")
    override suspend fun all(): List<Card> = error("unused")
    override suspend fun count(): Int = error("unused")
    override suspend fun ensureCatalogReady() = error("unused")
    override suspend fun resolveByIds(ids: Collection<Int>) = error("unused")
    override suspend fun resolveByName(name: String): Card? = error("unused")
    override suspend fun localized(id: Int, language: com.ygochecker.core.model.AppLanguage): Card? = error("unused")
}

private class FakeRelated(private val byCardId: Map<Int, List<RelatedCardRef>>) : GetRelatedCards {
    override suspend fun invoke(cardId: Int, format: GameFormat, limit: Int): List<RelatedCardRef> =
        byCardId[cardId].orEmpty()
}
