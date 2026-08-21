package com.ygochecker.core.domain

import com.ygochecker.core.model.ComboAssistEngine
import com.ygochecker.core.model.ComboLineAdvice
import com.ygochecker.core.model.DeckComboReport
import com.ygochecker.core.model.GameFormat
import kotlinx.coroutines.flow.first
import javax.inject.Inject

fun interface SuggestCombosForCard {
    suspend fun invoke(
        cardId: Int,
        deckId: Long?,
        format: GameFormat,
    ): List<ComboLineAdvice>
}

class DefaultSuggestCombosForCard @Inject constructor(
    private val catalog: FlowCatalog,
    private val decks: DeckRepository,
) : SuggestCombosForCard {
    override suspend fun invoke(
        cardId: Int,
        deckId: Long?,
        format: GameFormat,
    ): List<ComboLineAdvice> {
        val deckCounts = deckCounts(deckId)
        val graphs = catalog.graphs(format)
        val roles = catalog.allRoles(format)
        return ComboAssistEngine.adviseForCard(cardId, deckCounts, graphs, roles)
    }

    private suspend fun deckCounts(deckId: Long?): Map<Int, Int> {
        if (deckId == null) return emptyMap()
        val deck = decks.observeDeck(deckId).first() ?: return emptyMap()
        return deck.cards
            .groupBy { it.card.id }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    }
}

fun interface AnalyzeDeckCombos {
    suspend fun invoke(deckId: Long, format: GameFormat): DeckComboReport
}

class DefaultAnalyzeDeckCombos @Inject constructor(
    private val catalog: FlowCatalog,
    private val decks: DeckRepository,
) : AnalyzeDeckCombos {
    override suspend fun invoke(deckId: Long, format: GameFormat): DeckComboReport {
        val deck = decks.observeDeck(deckId).first()
        val deckCounts = deck?.cards
            ?.groupBy { it.card.id }
            ?.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            .orEmpty()
        return ComboAssistEngine.adviseForDeck(
            deckCounts = deckCounts,
            graphs = catalog.graphs(format),
            roles = catalog.allRoles(format),
        )
    }
}
