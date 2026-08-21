package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult
import com.ygochecker.core.common.valueOrNull
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.FlowEdge
import com.ygochecker.core.model.FlowGraph
import com.ygochecker.core.model.FlowKind
import com.ygochecker.core.model.FlowMetrics
import com.ygochecker.core.model.FlowNode
import com.ygochecker.core.model.FlowSummary
import com.ygochecker.core.model.FormatCardRole
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.toSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateDeckFlowsTest {
    private val myrmeleo = Card(91812389, "Traptrix Myrmeleo", "Effect Monster")
    private val hole = Card(29616900, "Traptrix Trap Hole Nightmare", "Normal Trap")
    private val unrelated = Card(1, "Blue-Eyes", "Normal Monster")

    private val flow = FlowGraph(
        id = "hat-myrmeleo-opening",
        formatId = "hat",
        title = "Myrmeleo",
        kind = FlowKind.OPENING,
        metrics = FlowMetrics(steps = 2),
        startNodeId = "a",
        nodes = mapOf(
            "a" to FlowNode("a", 0, "NS", cardIds = listOf(myrmeleo.id)),
            "b" to FlowNode("b", 1, "Hole", cardIds = listOf(hole.id)),
        ),
        edges = listOf(FlowEdge("e", "a", "b", "go", isDefault = true)),
    )

    @Test
    fun `links opening flow when deck shares starter`() = runBlocking {
        val deck = Decklist(
            id = 7L,
            name = "Traptrix",
            updatedAt = 0,
            cards = listOf(DeckCard(myrmeleo, 3, DeckSection.MAIN)),
        )
        val links = FakeLinks()
        val useCase = DefaultGenerateDeckFlows(
            decks = object : DeckRepository by ThrowingDecks {
                override fun observeDeck(id: Long) = flowOf(deck)
            },
            catalog = object : FlowCatalog {
                override suspend fun list(format: GameFormat) = listOf(flow.toSummary())
                override suspend fun get(flowId: String) = flow.takeIf { it.id == flowId }
                override suspend fun graphs(format: GameFormat) = listOf(flow)
                override fun rolesFor(cardId: Int, format: GameFormat): List<FormatCardRole> = emptyList()
                override fun allRoles(format: GameFormat): List<FormatCardRole> = emptyList()
            },
            links = links,
        )
        val result = useCase.invoke(7L, GameFormat.HAT).valueOrNull!!
        assertEquals(listOf("hat-myrmeleo-opening"), result.linkedFlowIds)
        assertEquals(listOf(hole.id), result.suggestedCardIds)
        assertEquals(listOf("hat-myrmeleo-opening"), links.stored[7L])
    }

    @Test
    fun `no link when deck has no flow cards`() = runBlocking {
        val deck = Decklist(
            id = 8L,
            name = "BE",
            updatedAt = 0,
            cards = listOf(DeckCard(unrelated, 3, DeckSection.MAIN)),
        )
        val links = FakeLinks()
        val useCase = DefaultGenerateDeckFlows(
            decks = object : DeckRepository by ThrowingDecks {
                override fun observeDeck(id: Long) = flowOf(deck)
            },
            catalog = object : FlowCatalog {
                override suspend fun list(format: GameFormat) = listOf(flow.toSummary())
                override suspend fun get(flowId: String) = flow
                override suspend fun graphs(format: GameFormat) = listOf(flow)
                override fun rolesFor(cardId: Int, format: GameFormat): List<FormatCardRole> = emptyList()
                override fun allRoles(format: GameFormat): List<FormatCardRole> = emptyList()
            },
            links = links,
        )
        val result = useCase.invoke(8L, GameFormat.HAT).valueOrNull!!
        assertTrue(result.linkedFlowIds.isEmpty())
        assertEquals(emptyList<String>(), links.stored[8L])
    }

    private class FakeLinks : DeckFlowLinkRepository {
        val stored = mutableMapOf<Long, List<String>>()
        override suspend fun replaceLinks(deckId: Long, flowIds: List<String>) {
            stored[deckId] = flowIds
        }
        override suspend fun flowIdsForDeck(deckId: Long) = stored[deckId].orEmpty()
        override suspend fun clearDeck(deckId: Long) { stored.remove(deckId) }
        override fun observeFlowIds(deckId: Long): Flow<List<String>> =
            MutableStateFlow(stored[deckId].orEmpty())
    }

    /** Minimal stub — only observeDeck is used. */
    private object ThrowingDecks : DeckRepository {
        override fun observeDecks() = flowOf(emptyList<Decklist>())
        override fun observeDeck(id: Long) = flowOf(null)
        override suspend fun create(name: String) = error("no")
        override suspend fun rename(id: Long, name: String) = error("no")
        override suspend fun delete(id: Long) = error("no")
        override suspend fun setCard(id: Long, card: Card, quantity: Int, section: DeckSection) = error("no")
        override suspend fun setCoverCards(id: Long, coverCardIds: List<Int>) = error("no")
        override suspend fun setPublic(id: Long, isPublic: Boolean) = error("no")
        override suspend fun persistImported(name: String, cards: List<DeckCard>) = AppResult.Err(com.ygochecker.core.common.AppError("x"))
        override suspend fun importText(text: String) = AppResult.Err(com.ygochecker.core.common.AppError("x"))
        override fun exportText(cards: List<DeckCard>) = ""
        override suspend fun importYdke(uri: String) = AppResult.Err(com.ygochecker.core.common.AppError("x"))
        override fun exportYdke(cards: List<DeckCard>) = ""
        override suspend fun importYdk(text: String) = AppResult.Err(com.ygochecker.core.common.AppError("x"))
        override fun exportYdk(cards: List<DeckCard>) = ""
    }
}
