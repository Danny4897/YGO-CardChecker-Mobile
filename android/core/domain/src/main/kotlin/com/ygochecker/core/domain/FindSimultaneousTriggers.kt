package com.ygochecker.core.domain

import com.ygochecker.core.model.SegocProfileSummary
import com.ygochecker.core.model.SimultaneousTriggerPair
import com.ygochecker.core.model.findSimultaneousTriggerPairs
import kotlinx.coroutines.flow.first
import javax.inject.Inject

fun interface FindSimultaneousTriggers {
    suspend fun invoke(deckId: Long): List<SimultaneousTriggerPair>
}

class DefaultFindSimultaneousTriggers @Inject constructor(
    private val decks: DeckRepository,
    private val segocProfiles: GetSegocProfiles,
) : FindSimultaneousTriggers {
    override suspend fun invoke(deckId: Long): List<SimultaneousTriggerPair> {
        val deck = decks.observeDeck(deckId).first() ?: return emptyList()
        val cardIds = deck.cards.map { it.card.id }.distinct()
        if (cardIds.isEmpty()) return emptyList()
        val profiles: Map<Int, SegocProfileSummary> = segocProfiles.invoke(cardIds).associateBy { it.cardId }
        return findSimultaneousTriggerPairs(profiles)
    }
}
