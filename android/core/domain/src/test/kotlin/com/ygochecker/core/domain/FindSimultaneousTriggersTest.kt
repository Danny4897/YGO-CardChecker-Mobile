package com.ygochecker.core.domain

import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.SegocEffectType
import com.ygochecker.core.model.SegocProfileSummary
import com.ygochecker.core.model.TriggerEvent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FindSimultaneousTriggersTest {
    @Test
    fun `returns pairs for the deck's own trigger cards`() = runBlocking {
        val deck = Decklist(
            id = 1L,
            name = "t",
            updatedAt = 0L,
            cards = listOf(
                DeckCard(Card(1, "c1", "Effect Monster"), 1, DeckSection.MAIN),
                DeckCard(Card(2, "c2", "Effect Monster"), 1, DeckSection.MAIN),
            ),
        )
        val decks = TriggerPairFakeDecks(deck)
        val profiles = object : GetSegocProfiles {
            override suspend fun invoke(ids: Collection<Int>) = listOf(
                SegocProfileSummary(1, SegocEffectType.TRIGGER, 1, true, listOf(TriggerEvent.DESTROYED)),
                SegocProfileSummary(2, SegocEffectType.TRIGGER, 1, true, listOf(TriggerEvent.DESTROYED)),
            )
        }
        val useCase = DefaultFindSimultaneousTriggers(decks, profiles)
        val result = useCase.invoke(deckId = 1L)
        assertEquals(1, result.size)
        assertEquals(TriggerEvent.DESTROYED, result[0].sharedEvent)
    }
}

private class TriggerPairFakeDecks(private val deck: Decklist) : DeckRepository by UnsupportedTriggerPairDeckRepository {
    override fun observeDeck(id: Long) = flowOf(deck.takeIf { it.id == id })
}

private object UnsupportedTriggerPairDeckRepository : DeckRepository {
    override fun observeDecks() = error("unused")
    override fun observeDeck(id: Long) = error("unused")
    override suspend fun create(name: String) = error("unused")
    override suspend fun rename(id: Long, name: String) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
    override suspend fun setCard(
        id: Long,
        card: Card,
        quantity: Int,
        section: DeckSection,
    ) = error("unused")
    override suspend fun setCoverCards(id: Long, coverCardIds: List<Int>) = error("unused")
    override suspend fun setPublic(id: Long, isPublic: Boolean) = error("unused")
    override suspend fun persistImported(
        name: String,
        cards: List<DeckCard>,
    ) = error("unused")
    override suspend fun importText(text: String) = error("unused")
    override fun exportText(cards: List<DeckCard>) = error("unused")
    override suspend fun importYdke(uri: String) = error("unused")
    override fun exportYdke(cards: List<DeckCard>) = error("unused")
    override suspend fun importYdk(text: String) = error("unused")
    override fun exportYdk(cards: List<DeckCard>) = error("unused")
}
