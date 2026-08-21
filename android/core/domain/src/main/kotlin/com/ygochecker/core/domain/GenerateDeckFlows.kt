package com.ygochecker.core.domain

import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.model.FlowKind
import com.ygochecker.core.model.FlowSummary
import com.ygochecker.core.model.GameFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DeckFlowGeneration(
    val linkedFlowIds: List<String>,
    val summaries: List<FlowSummary>,
    /** Cards from matched flows that were missing from the deck (suggestions only). */
    val suggestedCardIds: List<Int>,
)

/**
 * Match curated Flows to a deck by shared passcodes, persist links, return suggestions.
 * Separate from CompleteDeck (option C).
 */
fun interface GenerateDeckFlows {
    suspend fun invoke(deckId: Long, format: GameFormat): AppResult<DeckFlowGeneration>
}

class DefaultGenerateDeckFlows @Inject constructor(
    private val decks: DeckRepository,
    private val catalog: FlowCatalog,
    private val links: DeckFlowLinkRepository,
) : GenerateDeckFlows {
    override suspend fun invoke(deckId: Long, format: GameFormat): AppResult<DeckFlowGeneration> {
        val deck = decks.observeDeck(deckId).first()
            ?: return AppResult.Err(AppError("deck.not_found"))
        val deckIds = deck.cards.map { it.card.id }.toSet()
        if (deckIds.isEmpty()) {
            return AppResult.Ok(DeckFlowGeneration(emptyList(), emptyList(), emptyList()))
        }

        val matched = mutableListOf<FlowSummary>()
        val suggested = linkedSetOf<Int>()
        for (summary in catalog.list(format)) {
            val graph = catalog.get(summary.id) ?: continue
            val flowCards = graph.nodes.values.flatMap { it.cardIds }.toSet()
            if (flowCards.isEmpty()) continue
            val hit = flowCards.intersect(deckIds)
            val ok = when {
                hit.size >= 2 -> true
                hit.size == 1 && graph.kind in setOf(
                    FlowKind.OPENING,
                    FlowKind.ARTIFACT_ENGINE,
                    FlowKind.BAIT_REACTION,
                ) -> true
                else -> false
            }
            if (!ok) continue
            matched += summary
            suggested += (flowCards - deckIds)
        }

        val ids = matched.map { it.id }.distinct()
        links.replaceLinks(deckId, ids)
        return AppResult.Ok(
            DeckFlowGeneration(
                linkedFlowIds = ids,
                summaries = matched,
                suggestedCardIds = suggested.toList(),
            ),
        )
    }
}

interface DeckFlowLinkRepository {
    suspend fun replaceLinks(deckId: Long, flowIds: List<String>)
    suspend fun flowIdsForDeck(deckId: Long): List<String>
    suspend fun clearDeck(deckId: Long)
    fun observeFlowIds(deckId: Long): Flow<List<String>>
}
