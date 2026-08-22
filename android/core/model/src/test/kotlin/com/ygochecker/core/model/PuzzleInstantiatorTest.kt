package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleInstantiatorTest {
    private fun trigger(
        cardId: Int,
        event: TriggerEvent,
        missed: Boolean = false,
    ) = SegocProfileSummary(
        cardId = cardId,
        effectType = SegocEffectType.TRIGGER,
        spellSpeed = 1,
        missedTimingRisk = missed,
        triggerEvents = listOf(event),
    )

    @Test
    fun `lifo_your_two expected chain is low id then high id`() {
        val profiles = mapOf(
            5 to trigger(5, TriggerEvent.DESTROYED),
            9 to trigger(9, TriggerEvent.DESTROYED),
        )
        val puzzles = instantiatePuzzles(listOf(5, 9), emptyList(), profiles)
        val lifo = puzzles.first { it.templateId == "lifo_your_two" }
        assertEquals(listOf(5, 9), lifo.expectedChain)
        assertTrue(evaluatePuzzle(lifo, listOf(5, 9)))
        assertFalse(evaluatePuzzle(lifo, listOf(9, 5)))
    }

    @Test
    fun `apnap requires all your cards before any opp card`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.TO_GRAVE),
            2 to trigger(2, TriggerEvent.TO_GRAVE),
        )
        val puzzles = instantiatePuzzles(listOf(1), listOf(2), profiles)
        val apnap = puzzles.first { it.templateId == "apnap_you_vs_opp" }
        assertTrue(evaluatePuzzle(apnap, listOf(1, 2)))
        assertFalse(evaluatePuzzle(apnap, listOf(2, 1)))
    }

    @Test
    fun `missed timing puzzle requires the when-card as CL1`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED, missed = true),
            2 to trigger(2, TriggerEvent.DESTROYED, missed = false),
        )
        val puzzles = instantiatePuzzles(listOf(1, 2), emptyList(), profiles)
        val miss = puzzles.first { it.templateId == "missed_timing_when" }
        assertTrue(evaluatePuzzle(miss, listOf(1, 2)))
        assertFalse(evaluatePuzzle(miss, listOf(2, 1)))
    }

    @Test
    fun `OTHER and empty decks produce no puzzles`() {
        assertTrue(instantiatePuzzles(emptyList(), emptyList(), emptyMap()).isEmpty())
    }
}
