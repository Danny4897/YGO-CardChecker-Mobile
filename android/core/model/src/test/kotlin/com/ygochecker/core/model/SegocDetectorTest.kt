package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegocDetectorTest {
    private fun trigger(cardId: Int, event: TriggerEvent) = SegocProfileSummary(
        cardId = cardId,
        effectType = SegocEffectType.TRIGGER,
        spellSpeed = 1,
        missedTimingRisk = true,
        triggerEvents = listOf(event),
    )

    @Test
    fun `two trigger cards sharing an event form a pair`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            2 to trigger(2, TriggerEvent.DESTROYED),
        )
        val pairs = findSimultaneousTriggerPairs(profiles)
        assertEquals(1, pairs.size)
        assertEquals(TriggerEvent.DESTROYED, pairs[0].sharedEvent)
        assertEquals(setOf(1, 2), setOf(pairs[0].cardAId, pairs[0].cardBId))
    }

    @Test
    fun `cards with different events do not pair`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            2 to trigger(2, TriggerEvent.TO_GRAVE),
        )
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }

    @Test
    fun `non-trigger effect types are excluded even with a shared event`() {
        val ignition = SegocProfileSummary(
            cardId = 3,
            effectType = SegocEffectType.IGNITION,
            spellSpeed = 1,
            missedTimingRisk = false,
            triggerEvents = listOf(TriggerEvent.DESTROYED),
        )
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            3 to ignition,
        )
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }

    @Test
    fun `OTHER event never pairs, even with itself`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.OTHER),
            2 to trigger(2, TriggerEvent.OTHER),
        )
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }

    @Test
    fun `three cards sharing an event produce three pairs`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.TO_GRAVE),
            2 to trigger(2, TriggerEvent.TO_GRAVE),
            3 to trigger(3, TriggerEvent.TO_GRAVE),
        )
        assertEquals(3, findSimultaneousTriggerPairs(profiles).size)
    }

    @Test
    fun `a card is never paired with itself`() {
        val profiles = mapOf(1 to trigger(1, TriggerEvent.DESTROYED))
        assertTrue(findSimultaneousTriggerPairs(profiles).isEmpty())
    }
}
