package com.ygochecker.core.model

data class SimultaneousTriggerPair(
    val cardAId: Int,
    val cardBId: Int,
    val sharedEvent: TriggerEvent,
)

/**
 * Pure decklist-composition heads-up: which pairs of cards share a real trigger event
 * (per [SegocProfileSummary.triggerEvents]) and could therefore go on the chain together
 * under SEGOC. Does not simulate board state — a deck-composition signal, not a board fact.
 */
fun findSimultaneousTriggerPairs(cardProfiles: Map<Int, SegocProfileSummary>): List<SimultaneousTriggerPair> {
    val triggerCards = cardProfiles.values.filter { it.effectType == SegocEffectType.TRIGGER }
    val byEvent = HashMap<TriggerEvent, MutableList<Int>>()
    for (profile in triggerCards) {
        for (event in profile.triggerEvents) {
            if (event == TriggerEvent.OTHER) continue
            byEvent.getOrPut(event) { mutableListOf() }.add(profile.cardId)
        }
    }
    val out = mutableListOf<SimultaneousTriggerPair>()
    for ((event, cardIds) in byEvent) {
        val distinctIds = cardIds.distinct()
        for (i in distinctIds.indices) {
            for (j in i + 1 until distinctIds.size) {
                out += SimultaneousTriggerPair(distinctIds[i], distinctIds[j], event)
            }
        }
    }
    return out
}
