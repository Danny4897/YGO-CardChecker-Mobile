package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult
import com.ygochecker.core.common.valueOrNull
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckLegality
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.EffectScriptSummary
import com.ygochecker.core.model.FlowGraph
import com.ygochecker.core.model.FlowSummary
import com.ygochecker.core.model.FormatCardRole
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.RelatedCardRef
import com.ygochecker.core.model.SearchFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private object EmptyFlowCatalog : FlowCatalog {
    override suspend fun list(format: GameFormat): List<FlowSummary> = emptyList()
    override suspend fun get(flowId: String): FlowGraph? = null
    override suspend fun graphs(format: GameFormat): List<FlowGraph> = emptyList()
    override fun rolesFor(cardId: Int, format: GameFormat): List<FormatCardRole> = emptyList()
    override fun allRoles(format: GameFormat): List<FormatCardRole> = emptyList()
}

class SynergyCompleteDeckTest {
    private val seed = Card(1, "Elemental HERO Stratos", "Effect Monster", description = "If this card is Normal or Special Summoned: You can add 1 \"HERO\" monster from your Deck to your hand.")
    private val heroA = Card(2, "Elemental HERO Bubbleman", "Effect Monster", description = "If this is the only card in your hand, you can Special Summon this card from your hand.")
    private val poly = Card(3, "Polymerization", "Normal Spell", description = "Fusion Summon 1 Fusion Monster from your Extra Deck, using monsters from your hand or field as Fusion Material.")
    private val extraA = Card(4, "Elemental HERO Absolute Zero", "Fusion Monster", description = "1 \"HERO\" monster + 1 WATER monster")
    private val darkRulerDeal = Card(5, "A Deal with Dark Ruler", "Quick-Play Spell", description = "Activate only when you Special Summon \"Dark Ruler Ha Des\".")
    private val berserk = Card(6, "Berserk Mode", "Continuous Trap", description = "Activate only when \"Dark Ruler Ha Des\" is on the field.")
    private val tuner = Card(7, "Junk Synchron", "Tuner Monster", description = "When this card is Normal Summoned: You can target 1 Level 2 or lower monster in your GY; Special Summon it.")
    private val synchro = Card(8, "Junk Warrior", "Synchro Monster", description = "\"Junk Synchron\" + 1 or more non-Tuner monsters")
    private val rescue = Card(9, "Dramatic Rescue", "Normal Trap", description = "Activate only when a card is activated that targets an Amazoness monster.")

    private val catalog = listOf(
        seed, heroA, poly, extraA, darkRulerDeal, berserk, tuner, synchro, rescue,
    ).associateBy { it.id }

    private fun rel(id: Int, score: Double) =
        RelatedCardRef(id, catalog.getValue(id).name, "synergy", score)

    private val xyz = Card(
        11,
        "Number 39: Utopia",
        "Xyz Monster",
        level = 4,
        description = "2 Level 4 monsters",
    )
    private val synchro2 = Card(
        12,
        "Ally of Justice Catastor",
        "Synchro Monster",
        level = 5,
        description = "1 Tuner + 1 or more non-Tuner monsters",
    )

    @Test
    fun `extra prefers single copies and xyz when main has matching levels`() = runBlocking {
        val lv4a = Card(20, "Level Four A", "Effect Monster", level = 4, description = "A monster.")
        val lv4b = Card(21, "Level Four B", "Effect Monster", level = 4, description = "A monster.")
        val lv4c = Card(22, "Level Four C", "Effect Monster", level = 4, description = "A monster.")
        val all = catalog + mapOf(
            xyz.id to xyz,
            synchro2.id to synchro2,
            tuner.id to tuner,
            synchro.id to synchro,
            lv4a.id to lv4a,
            lv4b.id to lv4b,
            lv4c.id to lv4c,
        )
        val deck = Decklist(
            id = 1L,
            name = "Ranks",
            updatedAt = 0L,
            cards = listOf(
                DeckCard(tuner, 1, DeckSection.MAIN),
                DeckCard(lv4a, 2, DeckSection.MAIN),
                DeckCard(lv4b, 2, DeckSection.MAIN),
                DeckCard(lv4c, 1, DeckSection.MAIN),
            ),
        )
        val decks = MutableDeckRepository(deck)
        fun r(id: Int, score: Double) = RelatedCardRef(id, all.getValue(id).name, "synergy", score)
        val graph = mapOf(
            tuner.id to listOf(r(synchro.id, 20.0), r(synchro2.id, 19.0), r(xyz.id, 8.0)),
            lv4a.id to listOf(r(xyz.id, 18.0), r(synchro.id, 15.0)),
            lv4b.id to listOf(r(xyz.id, 17.0)),
            lv4c.id to listOf(r(xyz.id, 16.0)),
            synchro.id to emptyList(),
            synchro2.id to emptyList(),
            xyz.id to emptyList(),
        )
        val useCase = SynergyCompleteDeck(
            decks = decks,
            cards = FakeCards(all),
            related = GetRelatedCards { id, _, limit -> graph[id].orEmpty().take(limit) },
            scripts = GetEffectScripts { emptyList() },
            legality = AlwaysLegal,
            resolve = ResolveCardById { all[it] },
            flowCatalog = EmptyFlowCatalog,
        )
        useCase.invoke(1L, 10, 6, 0, GameFormat.TCG)
        val extra = decks.current.cards.filter { it.section == DeckSection.EXTRA }
        assertTrue(extra.isNotEmpty())
        assertTrue("Extra must be 1-ofs", extra.all { it.quantity == 1 })
        assertTrue(
            "Should include an Xyz when Main has Rank 4 material",
            extra.any { it.card.type.contains("Xyz", ignoreCase = true) },
        )
        assertTrue(
            "Should diversify beyond a single Extra name",
            extra.map { it.card.id }.distinct().size >= minOf(2, extra.size),
        )
    }

    @Test
    fun `hero seed prefers HERO fusion path over random rescue`() = runBlocking {
        val deck = Decklist(
            id = 1L,
            name = "Hero",
            updatedAt = 0L,
            cards = listOf(DeckCard(seed, 2, DeckSection.MAIN)),
        )
        val decks = MutableDeckRepository(deck)
        val relatedGraph = mapOf(
            1 to listOf(rel(2, 8.0), rel(3, 9.0), rel(4, 10.0), rel(9, 12.0), rel(5, 7.0)),
            2 to listOf(rel(3, 8.0), rel(4, 9.0)),
            3 to listOf(rel(4, 11.0)),
            4 to emptyList(),
            5 to listOf(rel(6, 15.0)),
            9 to emptyList(),
        )
        val useCase = SynergyCompleteDeck(
            decks = decks,
            cards = FakeCards(catalog),
            related = GetRelatedCards { id, _, limit -> relatedGraph[id].orEmpty().take(limit) },
            scripts = GetEffectScripts { ids ->
                ids.map { EffectScriptSummary(it, catalog[it]?.name.orEmpty(), listOf("engine"), emptyList()) }
            },
            legality = AlwaysLegal,
            resolve = ResolveCardById { catalog[it] },
            flowCatalog = EmptyFlowCatalog,
        )

        val plan = useCase.invoke(1L, 8, 3, 0, GameFormat.TCG).valueOrNull!!
        val names = decks.current.cards.map { it.card.name }
        assertTrue(names.any { it.contains("HERO") || it == "Polymerization" })
        assertTrue(names.none { it.contains("Rescue", ignoreCase = true) })
        assertTrue(plan.extraTotal >= 1)
    }

    @Test
    fun `synchro extra waits for tuner`() = runBlocking {
        val deck = Decklist(
            id = 1L,
            name = "Sync",
            updatedAt = 0L,
            cards = listOf(DeckCard(seed.copy(id = 10, name = "Dummy Warrior", description = "A warrior."), 1, DeckSection.MAIN)),
        )
        catalog.toMutableMap() // keep
        val cards = catalog + (10 to deck.cards.first().card)
        val decks = MutableDeckRepository(deck)
        val relatedGraph = mapOf(
            10 to listOf(rel(8, 20.0), rel(7, 5.0)),
            7 to listOf(rel(8, 12.0)),
            8 to emptyList(),
        )
        val useCase = SynergyCompleteDeck(
            decks = decks,
            cards = FakeCards(cards),
            related = GetRelatedCards { id, _, limit -> relatedGraph[id].orEmpty().take(limit) },
            scripts = GetEffectScripts { emptyList() },
            legality = AlwaysLegal,
            resolve = ResolveCardById { cards[it] },
            flowCatalog = EmptyFlowCatalog,
        )
        useCase.invoke(1L, 5, 2, 0, GameFormat.TCG)
        val extra = decks.current.cards.filter { it.section == DeckSection.EXTRA }
        val main = decks.current.cards.filter { it.section == DeckSection.MAIN }
        // If Synchro was added, a Tuner must exist in Main.
        if (extra.any { it.card.type.contains("Synchro") }) {
            assertTrue(main.any { it.card.type.contains("Tuner") })
        }
    }

    @Test
    fun `dark ruler package prefers berserk partner`() = runBlocking {
        val deck = Decklist(
            id = 1L,
            name = "Ruler",
            updatedAt = 0L,
            cards = listOf(DeckCard(darkRulerDeal, 1, DeckSection.MAIN)),
        )
        val decks = MutableDeckRepository(deck)
        val relatedGraph = mapOf(
            5 to listOf(rel(9, 10.0), rel(6, 4.0), rel(2, 3.0)),
            6 to emptyList(),
            9 to emptyList(),
        )
        val useCase = SynergyCompleteDeck(
            decks = decks,
            cards = FakeCards(catalog),
            related = GetRelatedCards { id, _, limit -> relatedGraph[id].orEmpty().take(limit) },
            scripts = GetEffectScripts { emptyList() },
            legality = AlwaysLegal,
            resolve = ResolveCardById { catalog[it] },
            flowCatalog = EmptyFlowCatalog,
        )
        useCase.invoke(1L, 6, 0, 0, GameFormat.TCG)
        val names = decks.current.cards.map { it.card.name.lowercase() }
        assertTrue(names.any { it.contains("berserk") })
        assertEquals(true, names.none { it.contains("rescue") } || names.any { it.contains("berserk") })
    }

    @Test
    fun `skips locked fusion bosses without materials and prefers rank staples`() = runBlocking {
        val lv4a = Card(40, "Plaguespreader Zombie", "Tuner Monster", race = "Zombie", level = 2, description = "You can banish this card from your GY; Special Summon 1 Level 4 Zombie monster.")
        val lv4b = Card(41, "Goblin Zombie", "Effect Monster", race = "Zombie", level = 4, description = "If this card is sent to the GY: Add 1 Zombie monster with 1200 or less DEF from your Deck to your hand.")
        val lv4c = Card(42, "Mezuki", "Effect Monster", race = "Zombie", level = 4, description = "You can banish this card from your GY, then target 1 Zombie monster in your GY; Special Summon that target.")
        val armityle = Card(
            43,
            "Armityle the Chaos Phantom",
            "Fusion Monster",
            description = "\"Uria, Lord of Searing Flames\" + \"Hamon, Lord of Striking Thunder\" + \"Raviel, Lord of Phantasms\"",
        )
        val barbaroid = Card(
            44,
            "Barbaroid, the Ultimate Battle Machine",
            "Fusion Monster",
            description = "5 Machine-Type monsters",
        )
        val ark = Card(
            45,
            "Number 101: Silent Honor ARK",
            "Xyz Monster",
            level = 4,
            description = "2 Level 4 monsters",
        )
        val all = catalog + mapOf(
            lv4a.id to lv4a,
            lv4b.id to lv4b,
            lv4c.id to lv4c,
            armityle.id to armityle,
            barbaroid.id to barbaroid,
            ark.id to ark,
        )
        val deck = Decklist(
            id = 1L,
            name = "Zombie",
            updatedAt = 0L,
            cards = listOf(
                DeckCard(lv4a, 1, DeckSection.MAIN),
                DeckCard(lv4b, 2, DeckSection.MAIN),
                DeckCard(lv4c, 2, DeckSection.MAIN),
            ),
        )
        val decks = MutableDeckRepository(deck)
        fun r(id: Int, score: Double) = RelatedCardRef(id, all.getValue(id).name, "synergy", score)
        val relatedGraph = mapOf(
            40 to listOf(r(43, 20.0), r(44, 18.0), r(45, 5.0)),
            41 to listOf(r(43, 15.0), r(45, 6.0)),
            42 to listOf(r(44, 15.0), r(45, 6.0)),
            43 to emptyList(),
            44 to emptyList(),
            45 to emptyList(),
        )
        val useCase = SynergyCompleteDeck(
            decks = decks,
            cards = FakeCards(all),
            related = GetRelatedCards { id, _, limit -> relatedGraph[id].orEmpty().take(limit) },
            scripts = GetEffectScripts { emptyList() },
            legality = AlwaysLegal,
            resolve = ResolveCardById { all[it] },
            flowCatalog = EmptyFlowCatalog,
        )
        useCase.invoke(1L, 8, 3, 0, GameFormat.TCG)
        val extraNames = decks.current.cards
            .filter { it.section == DeckSection.EXTRA }
            .map { it.card.name.lowercase() }
        assertTrue(extraNames.none { it.contains("armityle") })
        assertTrue(extraNames.none { it.contains("barbaroid") })
        assertTrue(extraNames.any { it.contains("101") || it.contains("silent honor") })
    }
}

private object AlwaysLegal : EvaluateDeckLegality {
    override fun maxCopies(cardId: Int, format: GameFormat) = 3
    override fun invoke(deck: Decklist, format: GameFormat) = DeckLegality(emptyList())
}

private class FakeCards(private val byId: Map<Int, Card>) : CardRepository {
    override fun search(query: String): Flow<List<Card>> = flowOf(emptyList())
    override fun search(query: String, format: GameFormat, filters: SearchFilters, limit: Int): Flow<List<Card>> =
        flowOf(emptyList())
    override fun browsePlayable(format: GameFormat, limit: Int): Flow<List<Card>> = flowOf(emptyList())
    override fun randomPlayable(format: GameFormat): Flow<Card?> = flowOf(null)
    override suspend fun get(id: Int) = byId[id]
    override suspend fun all() = byId.values.toList()
    override suspend fun count() = byId.size
    override suspend fun ensureCatalogReady() = Unit
    override suspend fun resolveByIds(ids: Collection<Int>) = AppResult.Ok(ids.mapNotNull { byId[it] })
    override suspend fun resolveByName(name: String) =
        byId.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: byId.values.firstOrNull { it.name.contains(name, ignoreCase = true) }
    override suspend fun localized(id: Int, language: AppLanguage) = byId[id]
}

private class MutableDeckRepository(initial: Decklist) : DeckRepository {
    private val state = MutableStateFlow(initial)
    val current: Decklist get() = state.value

    override fun observeDecks(): Flow<List<Decklist>> = emptyFlow()
    override fun observeDeck(id: Long): Flow<Decklist?> = state
    override suspend fun create(name: String): Long = error("unused")
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun setCard(id: Long, card: Card, quantity: Int, section: DeckSection) {
        val without = state.value.cards.filterNot { it.card.id == card.id && it.section == section }
        val nextCards = if (quantity <= 0) without else without + DeckCard(card, quantity, section)
        state.value = state.value.copy(cards = nextCards)
    }
    override suspend fun setCoverCards(id: Long, coverCardIds: List<Int>) = Unit
    override suspend fun setPublic(id: Long, isPublic: Boolean) = Unit
    override suspend fun persistImported(name: String, cards: List<DeckCard>) = error("unused")
    override suspend fun importText(text: String) = error("unused")
    override fun exportText(cards: List<DeckCard>) = error("unused")
    override suspend fun importYdke(uri: String) = error("unused")
    override fun exportYdke(cards: List<DeckCard>) = error("unused")
    override suspend fun importYdk(text: String) = error("unused")
    override fun exportYdk(cards: List<DeckCard>) = error("unused")
}
