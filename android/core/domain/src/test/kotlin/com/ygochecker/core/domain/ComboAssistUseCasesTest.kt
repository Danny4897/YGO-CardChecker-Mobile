package com.ygochecker.core.domain

import com.ygochecker.core.model.ComboActionKind
import com.ygochecker.core.model.FlowEdge
import com.ygochecker.core.model.FlowGraph
import com.ygochecker.core.model.FlowKind
import com.ygochecker.core.model.FlowMetrics
import com.ygochecker.core.model.FlowNode
import com.ygochecker.core.model.FlowPayoff
import com.ygochecker.core.model.FlowRisk
import com.ygochecker.core.model.FormatCardRole
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.HatCardRole
import com.ygochecker.core.model.toSummary
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboAssistUseCasesTest {
    private val myrmeleo = 91812389
    private val nightmare = 29616900

    private val flow = FlowGraph(
        id = "hat-myrmeleo-opening",
        formatId = "hat",
        title = "Traptrix opening",
        kind = FlowKind.OPENING,
        roleTags = listOf("Starter"),
        metrics = FlowMetrics(steps = 2, risk = FlowRisk.LOW, payoff = FlowPayoff.CONTROL),
        startNodeId = "m0",
        nodes = mapOf(
            "m0" to FlowNode(id = "m0", sortIndex = 0, title = "NS", cardIds = listOf(myrmeleo)),
            "m1" to FlowNode(id = "m1", sortIndex = 1, title = "Search", cardIds = listOf(nightmare)),
        ),
        edges = listOf(FlowEdge("e0", "m0", "m1", "go", isDefault = true)),
    )

    private val catalog = object : FlowCatalog {
        override suspend fun list(format: GameFormat) = listOf(flow.toSummary())
        override suspend fun get(flowId: String) = flow.takeIf { it.id == flowId }
        override suspend fun graphs(format: GameFormat) = listOf(flow)
        override fun rolesFor(cardId: Int, format: GameFormat) =
            allRoles(format).filter { it.cardId == cardId }
        override fun allRoles(format: GameFormat) = listOf(
            FormatCardRole(myrmeleo, "hat", HatCardRole.ENGINE_STARTER, 10, partnerIds = listOf(nightmare)),
        )
    }

    @Test
    fun `suggest combos for card uses deck counts`() = runBlocking {
        val decks = FakeDecks(
            mapOf(
                1L to listOf(myrmeleo to 3),
            ),
        )
        val useCase = DefaultSuggestCombosForCard(catalog, decks)
        val advice = useCase.invoke(myrmeleo, deckId = 1L, format = GameFormat.HAT)
        assertEquals(1, advice.size)
        assertEquals(listOf(nightmare), advice.single().missingKeyCardIds)
        assertTrue(advice.single().actions.any { it.kind == ComboActionKind.ADD_PARTNER })
    }

    @Test
    fun `analyze deck flattens recovery and partners`() = runBlocking {
        val decks = FakeDecks(mapOf(1L to listOf(myrmeleo to 2)))
        val report = DefaultAnalyzeDeckCombos(catalog, decks).invoke(1L, GameFormat.HAT)
        assertEquals(1, report.lines.size)
        assertTrue(report.actions.any { it.cardId == nightmare })
    }
}

private class FakeDecks(
    private val byId: Map<Long, List<Pair<Int, Int>>>,
) : DeckRepository by UnsupportedDeckRepository {
    override fun observeDeck(id: Long) = flowOf(
        byId[id]?.let { pairs ->
            com.ygochecker.core.model.Decklist(
                id = id,
                name = "t",
                updatedAt = 0L,
                cards = pairs.map { (cardId, qty) ->
                    com.ygochecker.core.model.DeckCard(
                        card = com.ygochecker.core.model.Card(cardId, "c$cardId", "Effect Monster"),
                        quantity = qty,
                        section = com.ygochecker.core.model.DeckSection.MAIN,
                    )
                },
            )
        },
    )
}

private object UnsupportedDeckRepository : DeckRepository {
    override fun observeDecks() = error("unused")
    override fun observeDeck(id: Long) = error("unused")
    override suspend fun create(name: String) = error("unused")
    override suspend fun rename(id: Long, name: String) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
    override suspend fun setCard(
        id: Long,
        card: com.ygochecker.core.model.Card,
        quantity: Int,
        section: com.ygochecker.core.model.DeckSection,
    ) = error("unused")
    override suspend fun setCoverCards(id: Long, coverCardIds: List<Int>) = error("unused")
    override suspend fun setPublic(id: Long, isPublic: Boolean) = error("unused")
    override suspend fun setPuzzleOpponent(id: Long, isPuzzleOpponent: Boolean) = error("unused")
    override suspend fun persistImported(
        name: String,
        cards: List<com.ygochecker.core.model.DeckCard>,
    ) = error("unused")
    override suspend fun importText(text: String) = error("unused")
    override fun exportText(cards: List<com.ygochecker.core.model.DeckCard>) = error("unused")
    override suspend fun importYdke(uri: String) = error("unused")
    override fun exportYdke(cards: List<com.ygochecker.core.model.DeckCard>) = error("unused")
    override suspend fun importYdk(text: String) = error("unused")
    override fun exportYdk(cards: List<com.ygochecker.core.model.DeckCard>) = error("unused")
}
