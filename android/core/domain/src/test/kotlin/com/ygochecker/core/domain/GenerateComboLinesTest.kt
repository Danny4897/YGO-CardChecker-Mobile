package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.CatalogSyncProgress
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.EffectScriptSummary
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.OfflinePackStatus
import com.ygochecker.core.model.RelatedCardRef
import com.ygochecker.core.model.SegocProfileSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateComboLinesTest {
    private val fillerId = 1
    private val userId = 2
    private val bystanderId = 3

    private val filler = Card(fillerId, "Foolish Burial", "Normal Spell")
    private val user = Card(userId, "Plaguespreader Zombie", "Effect Monster")
    private val bystander = Card(bystanderId, "Vanilla", "Normal Monster")

    private val deck = Decklist(
        id = 1L,
        name = "t",
        updatedAt = 0L,
        cards = listOf(
            DeckCard(filler, quantity = 1, section = DeckSection.MAIN),
            DeckCard(user, quantity = 1, section = DeckSection.MAIN),
            DeckCard(bystander, quantity = 1, section = DeckSection.SIDE),
        ),
    )

    @Test
    fun `generates a labeled line for a GY filler-user pair, ignores side deck`() = runBlocking {
        val decks = FakeDecks(mapOf(1L to deck))
        val pack = FakePack(
            mapOf(
                fillerId to listOf("sends_to_gy"),
                userId to listOf("ss_from_gy"),
                bystanderId to listOf("negates"), // in Side, must never influence the result
            ),
        )
        val useCase = DefaultGenerateComboLines(decks, pack)

        val lines = useCase.invoke(1L, GameFormat.HAT)

        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(listOf(fillerId, userId), line.cardIds)
        assertTrue(line.steps[0].contains("Foolish Burial"))
        assertTrue(line.steps[1].contains("Plaguespreader Zombie"))
    }

    @Test
    fun `no complementary pair yields no lines`() = runBlocking {
        val decks = FakeDecks(mapOf(1L to deck))
        val pack = FakePack(mapOf(fillerId to listOf("negates"), userId to listOf("destroys")))
        val lines = DefaultGenerateComboLines(decks, pack).invoke(1L, GameFormat.HAT)
        assertTrue(lines.isEmpty())
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
