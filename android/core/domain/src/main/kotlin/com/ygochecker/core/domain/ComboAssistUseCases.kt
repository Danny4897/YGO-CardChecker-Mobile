package com.ygochecker.core.domain

import com.ygochecker.core.model.ComboAssistEngine
import com.ygochecker.core.model.ComboLineAdvice
import com.ygochecker.core.model.ComboPlanner
import com.ygochecker.core.model.DeckComboReport
import com.ygochecker.core.model.DeckSection
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

/** One generated line, already resolved to readable step text — never merged with curated [com.ygochecker.core.model.ComboRecipe] content. */
data class GeneratedComboLineUi(val steps: List<String>, val cardIds: List<Int>)

/**
 * Computed combo lines (see [ComboPlanner]) for the deck's own cards — not hand-authored,
 * always labeled as such in the UI. Complements [AnalyzeDeckCombos], which only reports on
 * curated Flow/role content.
 */
fun interface GenerateComboLines {
    suspend fun invoke(deckId: Long, format: GameFormat, maxLines: Int): List<GeneratedComboLineUi>
}

class DefaultGenerateComboLines @Inject constructor(
    private val decks: DeckRepository,
    private val pack: OfflinePackRepository,
) : GenerateComboLines {
    override suspend fun invoke(deckId: Long, format: GameFormat, maxLines: Int): List<GeneratedComboLineUi> {
        val deck = decks.observeDeck(deckId).first() ?: return emptyList()
        val active = deck.cards.filter { it.section != DeckSection.SIDE }
        if (active.isEmpty()) return emptyList()
        val namesById = active.associate { it.card.id to it.card.name }
        val tagsById = pack.effectScripts(active.map { it.card.id })
            .associate { it.cardId to it.tags.toSet() }
        return ComboPlanner.plan(tagsById, maxLines).map { line ->
            GeneratedComboLineUi(steps = line.describe(namesById), cardIds = line.cardIds)
        }
    }
}
