package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SegocProfileTest {
    @Test
    fun `SegocEffectType has exactly the values the TS parser emits`() {
        val names = SegocEffectType.entries.map { it.name }
        assertEquals(
            setOf("ACTIVATE", "IGNITION", "TRIGGER", "QUICK", "CONTINUOUS", "NONE"),
            names.toSet(),
        )
    }

    @Test
    fun `TriggerEvent has exactly the values the TS parser emits`() {
        val names = TriggerEvent.entries.map { it.name }
        assertEquals(
            setOf(
                "DESTROYED", "TO_GRAVE", "REMOVED", "LEAVES_FIELD",
                "SUMMON_SUCCESS", "FLIP_SUMMON_SUCCESS", "SPECIAL_SUMMON_SUCCESS",
                "DISCARDED", "DRAWN", "DAMAGE", "CONTROL_CHANGED", "BATTLE_DESTROYED", "OTHER",
            ),
            names.toSet(),
        )
    }

    @Test
    fun `SegocProfileSummary carries the fields the coach needs`() {
        val summary = SegocProfileSummary(
            cardId = 11662742,
            effectType = SegocEffectType.TRIGGER,
            spellSpeed = 1,
            missedTimingRisk = true,
            triggerEvents = listOf(TriggerEvent.DESTROYED),
        )
        assertEquals(11662742, summary.cardId)
        assertEquals(SegocEffectType.TRIGGER, summary.effectType)
        assertEquals(1, summary.spellSpeed)
        assertEquals(true, summary.missedTimingRisk)
        assertEquals(listOf(TriggerEvent.DESTROYED), summary.triggerEvents)
    }

    @Test
    fun `spellSpeed is nullable for vanilla cards with no effect`() {
        val summary = SegocProfileSummary(
            cardId = 90000000,
            effectType = SegocEffectType.NONE,
            spellSpeed = null,
            missedTimingRisk = false,
            triggerEvents = emptyList(),
        )
        assertEquals(null, summary.spellSpeed)
    }
}
