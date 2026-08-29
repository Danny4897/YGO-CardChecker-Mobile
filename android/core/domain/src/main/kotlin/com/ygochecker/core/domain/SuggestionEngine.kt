package com.ygochecker.core.domain

import com.ygochecker.core.model.Card
import com.ygochecker.core.model.CardRoleClassifier
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.HatCardRole
import com.ygochecker.core.model.SearchFilters
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** How many copies of [role] a competitive deck typically wants, regardless of archetype. */
private val RECOMMENDED_MIN: Map<HatCardRole, Int> = mapOf(
    HatCardRole.INTERRUPT to 3,
    HatCardRole.REMOVAL to 2,
    HatCardRole.SEARCHER to 1,
    HatCardRole.EXTENDER to 1,
)

data class RoleGap(val role: HatCardRole, val currentCount: Int, val recommendedMin: Int)

data class DeckRoleGapReport(
    val roleCounts: Map<HatCardRole, Int>,
    val gaps: List<RoleGap>,
)

/**
 * Counts each active-deck card's functional role (curated [FormatCardRole] when available,
 * else [CardRoleClassifier] derived from its own tags) and flags roles under the recommended
 * floor. Works on any archetype/format — the floor is a mechanic-level baseline, not a
 * per-archetype curated table.
 */
fun interface AnalyzeDeckRoleGaps {
    suspend fun invoke(deckId: Long, format: GameFormat): DeckRoleGapReport
}

class DefaultAnalyzeDeckRoleGaps @Inject constructor(
    private val decks: DeckRepository,
    private val pack: OfflinePackRepository,
    private val catalog: FlowCatalog,
) : AnalyzeDeckRoleGaps {
    override suspend fun invoke(deckId: Long, format: GameFormat): DeckRoleGapReport {
        val deck = decks.observeDeck(deckId).first() ?: return DeckRoleGapReport(emptyMap(), emptyList())
        val active = deck.cards.filter { it.section != DeckSection.SIDE }
        if (active.isEmpty()) return DeckRoleGapReport(emptyMap(), emptyList())

        val curatedRoles = catalog.allRoles(format)
            .groupBy({ it.cardId }, { it.role })
            .mapValues { it.value.toSet() }
        val scripts = pack.effectScripts(active.map { it.card.id }).associateBy { it.cardId }

        val counts = linkedMapOf<HatCardRole, Int>()
        for (dc in active) {
            val roles = curatedRoles[dc.card.id]
                ?: CardRoleClassifier.classify(dc.card, scripts[dc.card.id]?.tags.orEmpty())
            for (role in roles) counts[role] = (counts[role] ?: 0) + dc.quantity
        }

        val gaps = RECOMMENDED_MIN.mapNotNull { (role, min) ->
            val have = counts[role] ?: 0
            if (have < min) RoleGap(role, have, min) else null
        }
        return DeckRoleGapReport(counts, gaps)
    }
}

data class CardSuggestion(
    val card: Card,
    val fillsRole: HatCardRole,
    val reason: String,
    val synergyScore: Double,
)

/**
 * Cards not yet in the deck that (a) fill an under-represented role and (b) are synergistically
 * linked to cards already in the deck — the two signals the user asked for combined into one
 * ranked list, explained in plain language.
 */
fun interface SuggestSynergisticCards {
    suspend fun invoke(deckId: Long, format: GameFormat, maxSuggestions: Int = 12): List<CardSuggestion>
}

class DefaultSuggestSynergisticCards @Inject constructor(
    private val decks: DeckRepository,
    private val cardRepo: CardRepository,
    private val pack: OfflinePackRepository,
    private val gaps: AnalyzeDeckRoleGaps,
    private val related: GetRelatedCards,
) : SuggestSynergisticCards {
    override suspend fun invoke(deckId: Long, format: GameFormat, maxSuggestions: Int): List<CardSuggestion> {
        val deck = decks.observeDeck(deckId).first() ?: return emptyList()
        val active = deck.cards.filter { it.section != DeckSection.SIDE }
        if (active.isEmpty()) return emptyList()
        val inDeckIds = active.map { it.card.id }.toSet()

        // Reverse synergy index: candidateId -> partner names/scores from cards already in deck.
        val synergyIndex = HashMap<Int, MutableList<Pair<String, Double>>>()
        for (dc in active) {
            related.invoke(dc.card.id, format, RELATED_POOL)
                .filterNot { it.id in inDeckIds }
                .forEach { ref -> synergyIndex.getOrPut(ref.id) { mutableListOf() } += dc.card.name to ref.score }
        }

        val report = gaps.invoke(deckId, format)
        if (report.gaps.isEmpty()) return emptyList()

        val out = mutableListOf<CardSuggestion>()
        val seen = HashSet<Int>()
        for (gap in report.gaps.sortedByDescending { it.recommendedMin - it.currentCount }) {
            val tags = ROLE_SEARCH_TAGS[gap.role] ?: continue
            for (tag in tags) {
                val candidates = cardRepo.search("", format, SearchFilters(effectTag = tag, playableOnly = true), CANDIDATE_POOL)
                    .first()
                    .filter { it.id !in inDeckIds && it.id !in seen }
                if (candidates.isEmpty()) continue
                val candidateTags = pack.effectScripts(candidates.map { it.id }).associateBy { it.cardId }
                for (card in candidates) {
                    val role = gap.role
                    val cardTags = candidateTags[card.id]?.tags.orEmpty()
                    if (role !in CardRoleClassifier.classify(card, cardTags)) continue
                    val partners = synergyIndex[card.id].orEmpty()
                    val score = partners.sumOf { it.second }
                    val partnerNames = partners.map { it.first }.distinct().take(3)
                    out += CardSuggestion(card, role, reasonFor(role, gap, partnerNames), score)
                    seen += card.id
                }
            }
        }
        return out.sortedByDescending { it.synergyScore }.take(maxSuggestions)
    }

    private fun reasonFor(role: HatCardRole, gap: RoleGap, partnerNames: List<String>): String {
        val roleLabel = role.name.lowercase().replace('_', ' ')
        val base = "Copre $roleLabel (${gap.currentCount}/${gap.recommendedMin} consigliate)"
        return if (partnerNames.isEmpty()) base else "$base — sinergico con ${partnerNames.joinToString(", ")}"
    }

    private companion object {
        const val RELATED_POOL = 10
        const val CANDIDATE_POOL = 24
        val ROLE_SEARCH_TAGS: Map<HatCardRole, List<String>> = mapOf(
            HatCardRole.INTERRUPT to listOf("negates"),
            HatCardRole.REMOVAL to listOf("destroys", "banishes"),
            HatCardRole.SEARCHER to listOf("searches_deck"),
            HatCardRole.EXTENDER to listOf("special_summons"),
        )
    }
}
