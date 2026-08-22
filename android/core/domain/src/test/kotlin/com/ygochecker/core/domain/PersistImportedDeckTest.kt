package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult
import com.ygochecker.core.common.valueOrNull
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistImportedDeckTest {
    @Test fun `persists imported cards through repository transaction`() = runBlocking {
        val cards = listOf(
            DeckCard(Card(46986414, "Dark Magician", "Normal Monster"), 2, DeckSection.MAIN),
        )
        val repository = RecordingDeckRepository()

        val result = DefaultPersistImportedDeck(repository).invoke("Imported deck", cards)

        assertEquals(42L, result.valueOrNull)
        assertEquals("Imported deck", repository.savedName)
        assertEquals(cards, repository.savedCards)
    }
}

private class RecordingDeckRepository : DeckRepository {
    var savedName: String? = null
    var savedCards: List<DeckCard>? = null

    override fun observeDecks(): Flow<List<Decklist>> = emptyFlow()
    override fun observeDeck(id: Long): Flow<Decklist?> = emptyFlow()
    override suspend fun create(name: String): Long = error("not used")
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun setCard(id: Long, card: Card, quantity: Int, section: DeckSection) = Unit
    override suspend fun setCoverCards(id: Long, coverCardIds: List<Int>) = Unit
    override suspend fun setPublic(id: Long, isPublic: Boolean) = Unit
    override suspend fun setPuzzleOpponent(id: Long, isPuzzleOpponent: Boolean) = Unit
    override suspend fun importText(text: String): AppResult<List<DeckCard>> = error("not used")
    override fun exportText(cards: List<DeckCard>): String = error("not used")
    override suspend fun importYdke(uri: String): AppResult<List<DeckCard>> = error("not used")
    override fun exportYdke(cards: List<DeckCard>): String = error("not used")
    override suspend fun importYdk(text: String): AppResult<List<DeckCard>> = error("not used")
    override fun exportYdk(cards: List<DeckCard>): String = error("not used")

    override suspend fun persistImported(name: String, cards: List<DeckCard>): AppResult<Long> {
        savedName = name
        savedCards = cards
        return AppResult.Ok(42L)
    }
}
